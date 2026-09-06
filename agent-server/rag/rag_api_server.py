"""FastAPI wrapper for local medical RAG retrieval.

Run:
    python -m rag.rag_api_server
    uvicorn rag.rag_api_server:app --host 0.0.0.0 --port 18080
"""

from __future__ import annotations

import re
import time
import uuid
from typing import Any

import uvicorn
from fastapi import FastAPI, Header, Response
from pydantic import BaseModel, Field

from .embedding_provider import EmbeddingProvider
from .milvus_client import MedicalRagMilvus, UserMemoryMilvus
from .rag_config import (
    MEDICAL_RAG_V3_COLLECTION_NAME,
    MEDICAL_RAG_V3_PARENT_ARTIFACT_PATH,
    MILVUS_COLLECTION_NAME,
    MILVUS_HOST,
    MILVUS_PORT,
    PARENT_CHILD_NEIGHBOR_AFTER,
    PARENT_CHILD_NEIGHBOR_BEFORE,
    CONTEXT_PARENT_MAX_TOKENS,
    MAX_CONTEXT_UNITS_PER_PARENT,
    MAX_CONTEXT_UNITS_PER_SOURCE,
    RAG_CONTEXT_MAX_TOKENS,
    RAG_CONTEXT_MODE,
    RAG_EXTERNAL_EVIDENCE_ENABLED,
    RAG_HYBRID_COLLECTION_NAME,
    RAG_RETRIEVAL_MODE,
    RAG_RRF_K,
    RAG_RESERVED_NON_RAG_TOKENS,
    RAG_TOTAL_MODEL_CONTEXT_LIMIT,
    USER_MEMORY_COLLECTION_NAME,
)
from .context.context_builder import ContextBuilder, ParentChildExpander
from .context.context_models import ContextPolicy
from .context.external_evidence_pipeline import ExternalEvidencePipeline
from .retrieval.query_expansion import expand_medical_query
from .retrieval.reranker import default_reranker_info
from .retrieval.retrieval_service import RetrievalService, clip_text, select_retrieval_chunks, structured_fields
from .retrieval.scene_policy import (
    DEFAULT_DOC_TYPES,
    MEDICAL_RECORD_DOC_TYPES,
    doc_type_limits,
    ordered_doc_types,
    quota_for_scene,
    retrieval_candidate_limit,
    selection_doc_type_caps,
)
from .schema.retrieval_models import RetrieveChunk, RetrieveRequest, RetrieveResponse
from .store.milvus_store import MilvusStore
from .store.parent_store import ParentStore


class MemoryUpsertRequest(BaseModel):
    collection: str = USER_MEMORY_COLLECTION_NAME
    text: str = Field(min_length=1)
    metadata: dict[str, Any]


class MemoryUpsertResponse(BaseModel):
    success: bool
    collection: str
    memory_id: str | None = None
    inserted_count: int = 0
    error_message: str | None = None


class MemorySearchRequest(BaseModel):
    collection: str = USER_MEMORY_COLLECTION_NAME
    query: str = Field(min_length=1)
    topK: int = Field(default=5, ge=1, le=30)
    filter: str


class MemorySearchResult(BaseModel):
    id: str | None = None
    text: str | None = None
    score: float | None = None
    metadata: dict[str, Any] = Field(default_factory=dict)


class MemorySearchResponse(BaseModel):
    success: bool
    collection: str
    results: list[MemorySearchResult] = Field(default_factory=list)
    error_message: str | None = None


class MemoryDeleteBySourceRequest(BaseModel):
    collection: str = USER_MEMORY_COLLECTION_NAME
    sourceId: str = Field(min_length=1)
    filter: str
    sourceType: str | None = None


class MemoryDeleteResponse(BaseModel):
    success: bool
    collection: str
    deleted_count: int = 0
    error_message: str | None = None


class MemoryHealthResponse(BaseModel):
    success: bool
    collection: str
    collection_exists: bool
    error_message: str | None = None


app = FastAPI(title="Medical RAG Retrieval Service", version="1.0")
provider: EmbeddingProvider | None = None
milvus: MedicalRagMilvus | None = None
user_memory_milvus: UserMemoryMilvus | None = None


def clip_memory_text(text: Any, limit: int = 16000) -> str:
    value = str(text or "").strip()
    return value if len(value) <= limit else value[:limit]


def safe_varchar(value: Any, limit: int, default: str = "") -> str:
    text = str(value or default).strip()
    return text[:limit]


def safe_int64(value: Any, default: int | None = None) -> int:
    if value is None or value == "":
        return int(default if default is not None else time.time() * 1000)
    try:
        return int(value)
    except (TypeError, ValueError):
        return int(default if default is not None else time.time() * 1000)


def safe_error_message(exc: Exception) -> str:
    text = str(exc)
    return text.encode("utf-8", errors="ignore").decode("utf-8", errors="ignore")


def safe_log(message: str) -> None:
    print(message.encode("gbk", errors="ignore").decode("gbk", errors="ignore"))


def patient_hash_from_filter(filter_expr: str) -> str | None:
    if not filter_expr:
        return None
    match = re.search(r"patientIdHash\s*==\s*(['\"])([^'\"]+)\1", filter_expr)
    return match.group(2) if match else None


def build_trace_meta(
    trace_id: str | None,
    run_id: str | None,
    step_id: str | None,
    session_id: str | None,
) -> dict[str, Any]:
    return {
        key: value
        for key, value in {
            "trace_id": trace_id,
            "run_id": run_id,
            "step_id": step_id,
            "session_id": session_id,
            "service": "medical-rag",
            "rag_version": "medical-rag-v1",
        }.items()
        if value
    }


def rag_collection_for_mode(mode: str = RAG_RETRIEVAL_MODE) -> str:
    return RAG_HYBRID_COLLECTION_NAME if mode in {"hybrid_rrf", "hybrid_rerank", "bm25"} else MILVUS_COLLECTION_NAME


def external_parent_store() -> ParentStore:
    from pathlib import Path

    artifact = Path(MEDICAL_RAG_V3_PARENT_ARTIFACT_PATH) if MEDICAL_RAG_V3_PARENT_ARTIFACT_PATH else None
    return ParentStore(artifact)


def context_policy_for_mode(mode: str) -> ContextPolicy:
    return ContextPolicy(
        mode="child_with_neighbors" if mode == "child_neighbors" else mode,
        neighbor_before=PARENT_CHILD_NEIGHBOR_BEFORE,
        neighbor_after=PARENT_CHILD_NEIGHBOR_AFTER,
        parent_max_tokens=CONTEXT_PARENT_MAX_TOKENS,
        max_tokens=RAG_CONTEXT_MAX_TOKENS,
        max_units_per_parent=MAX_CONTEXT_UNITS_PER_PARENT,
        max_units_per_source=MAX_CONTEXT_UNITS_PER_SOURCE,
        total_model_context_limit=RAG_TOTAL_MODEL_CONTEXT_LIMIT,
        reserved_non_rag_tokens=RAG_RESERVED_NON_RAG_TOKENS,
    )


def retrieve_external_context(query: str, expanded_query: str | None, top_k: int) -> tuple[list[RetrieveChunk], dict[str, Any]]:
    if provider is None:
        return [], {"enabled": False, "error": "Embedding provider not initialized"}
    mode = RAG_CONTEXT_MODE
    if not RAG_EXTERNAL_EVIDENCE_ENABLED or mode == "legacy":
        return [], {"enabled": False, "context_mode": mode}
    if mode not in {"child_only", "child_neighbors", "child_with_neighbors", "parent_section"}:
        return [], {"enabled": False, "context_mode": mode, "error": "unsupported_context_mode"}
    external_milvus = MedicalRagMilvus(MILVUS_HOST, MILVUS_PORT, MEDICAL_RAG_V3_COLLECTION_NAME)
    external_milvus.connect()
    if not external_milvus.has_collection():
        return [], {"enabled": True, "context_mode": mode, "collection": MEDICAL_RAG_V3_COLLECTION_NAME, "error": "collection_missing"}
    pipeline = ExternalEvidencePipeline(provider, MilvusStore(external_milvus), rrf_k=RAG_RRF_K)
    children, retrieval_trace = pipeline.search(query, expanded_query=expanded_query, top_k=top_k)
    parent_store = external_parent_store()
    policy = context_policy_for_mode(mode)
    units, expansion_trace = ParentChildExpander(parent_store).expand(children, policy)
    package = ContextBuilder().build([], units, policy)
    chunks = [
        RetrieveChunk(
            chunk_id="external_context:" + unit.parent_id if unit.parent_id else unit.context_unit_id,
            doc_id=unit.source_id,
            doc_type="external_evidence",
            title=unit.source_title,
            score=unit.best_rerank_score,
            final_score=unit.best_rerank_score,
            topic_alignment="external_evidence",
            selection_reason=unit.expansion_mode,
            chunk_text=clip_text(unit.text, 1600),
        )
        for unit in package.context_units
    ]
    trace = {
        "enabled": True,
        "context_mode": mode,
        "collection": MEDICAL_RAG_V3_COLLECTION_NAME,
        "retrieval": retrieval_trace,
        "parent_expansion": expansion_trace,
        "context_builder": package.trace,
        "included_chunk_ids": package.included_chunk_ids,
        "included_parent_ids": package.included_parent_ids,
        "included_source_ids": package.included_source_ids,
        "context_hash": package.context_hash,
        "context_tokens": package.token_count,
    }
    return chunks, trace


def ensure_user_memory_client(collection: str) -> UserMemoryMilvus:
    global user_memory_milvus
    if provider is None:
        raise RuntimeError("Embedding provider 尚未初始化")
    collection_name = safe_varchar(collection, 128, USER_MEMORY_COLLECTION_NAME) or USER_MEMORY_COLLECTION_NAME
    if user_memory_milvus is None or user_memory_milvus.collection_name != collection_name:
        user_memory_milvus = UserMemoryMilvus(MILVUS_HOST, MILVUS_PORT, collection_name)
        user_memory_milvus.connect()
    user_memory_milvus.ensure_collection(provider.embedding_dim)
    return user_memory_milvus


@app.on_event("startup")
def startup() -> None:
    global provider, milvus
    provider = EmbeddingProvider()
    collection_name = rag_collection_for_mode()
    milvus = MedicalRagMilvus(MILVUS_HOST, MILVUS_PORT, collection_name)
    try:
        milvus.connect()
        if not milvus.has_collection():
            safe_log(f"Warning: RAG collection missing: {collection_name}")
    except Exception as exc:
        safe_log(f"Warning: Milvus RAG collection unavailable during startup: {safe_error_message(exc)}")
        milvus = None


@app.get("/health")
def health() -> dict[str, Any]:
    exists = bool(milvus and milvus.has_collection())
    user_memory_exists = False
    try:
        if user_memory_milvus is not None:
            user_memory_exists = user_memory_milvus.has_collection()
    except Exception:
        user_memory_exists = False
    return {
        "success": exists,
        "collection": milvus.collection_name if milvus else rag_collection_for_mode(),
        "collection_exists": exists,
        "retrieval_mode": RAG_RETRIEVAL_MODE,
        "context_mode": RAG_CONTEXT_MODE,
        "external_evidence_enabled": RAG_EXTERNAL_EVIDENCE_ENABLED,
        "external_evidence_collection": MEDICAL_RAG_V3_COLLECTION_NAME,
        "reranker": default_reranker_info() if RAG_RETRIEVAL_MODE == "hybrid_rerank" else {"loaded": False},
        "user_memory_collection": USER_MEMORY_COLLECTION_NAME,
        "user_memory_collection_exists": user_memory_exists,
    }


@app.post("/rag/retrieve", response_model=RetrieveResponse)
def retrieve(
    request: RetrieveRequest,
    response: Response,
    x_agent_trace_id: str | None = Header(default=None),
    x_agent_run_id: str | None = Header(default=None),
    x_agent_step_id: str | None = Header(default=None),
    x_agent_session_id: str | None = Header(default=None),
) -> RetrieveResponse:
    trace_meta = build_trace_meta(x_agent_trace_id, x_agent_run_id, x_agent_step_id, x_agent_session_id)
    for header_name, header_value in {
        "X-Agent-Trace-Id": x_agent_trace_id,
        "X-Agent-Run-Id": x_agent_run_id,
        "X-Agent-Step-Id": x_agent_step_id,
        "X-Agent-Session-Id": x_agent_session_id,
    }.items():
        if header_value:
            response.headers[header_name] = header_value
    if provider is None or milvus is None:
        return RetrieveResponse(success=False, query=request.query, error_message="RAG 服务尚未连接 Milvus")
    if not milvus.has_collection():
        return RetrieveResponse(success=False, query=request.query, error_message=f"collection 不存在: {milvus.collection_name}")

    service = RetrievalService(provider, MilvusStore(milvus), retrieval_mode=RAG_RETRIEVAL_MODE, rrf_k=RAG_RRF_K)
    result = service.retrieve(request, trace_meta)
    if not result.success:
        return result
    try:
        external_chunks, external_trace = retrieve_external_context(request.query, result.expanded_query, min(request.top_k, 5))
    except Exception as exc:
        external_chunks = []
        external_trace = {
            "enabled": RAG_EXTERNAL_EVIDENCE_ENABLED,
            "context_mode": RAG_CONTEXT_MODE,
            "collection": MEDICAL_RAG_V3_COLLECTION_NAME,
            "error": safe_error_message(exc),
        }
    if external_chunks:
        result.chunks.extend(external_chunks)
        counts = dict(result.doc_type_counts or {})
        counts["external_evidence"] = counts.get("external_evidence", 0) + len(external_chunks)
        result.doc_type_counts = counts
    result.trace_meta = {
        **(result.trace_meta or {}),
        "context_mode": RAG_CONTEXT_MODE,
        "external_evidence": external_trace,
    }
    return result


@app.post("/memory/upsert", response_model=MemoryUpsertResponse)
def upsert_memory(request: MemoryUpsertRequest) -> MemoryUpsertResponse:
    try:
        metadata = request.metadata or {}
        patient_id_hash = safe_varchar(metadata.get("patientIdHash"), 128)
        if not patient_id_hash:
            return MemoryUpsertResponse(
                success=False,
                collection=request.collection,
                error_message="patientIdHash is required",
            )
        now = int(time.time() * 1000)
        memory_id = safe_varchar(
            metadata.get("memoryId")
            or metadata.get("id")
            or f"{patient_id_hash}:{metadata.get('sourceType', 'memory')}:{metadata.get('sourceId') or uuid.uuid4()}",
            128,
        )
        text = clip_memory_text(request.text)
        vector = provider.encode_texts([text])[0] if provider is not None else []
        client = ensure_user_memory_client(request.collection)
        row = {
            "memory_id": memory_id,
            "patientIdHash": patient_id_hash,
            "memoryLevel": safe_varchar(metadata.get("memoryLevel"), 32, "medium"),
            "sourceType": safe_varchar(metadata.get("sourceType"), 64, "visit_summary"),
            "sourceId": safe_varchar(metadata.get("sourceId"), 128),
            "department": safe_varchar(metadata.get("department"), 128),
            "eventTime": safe_int64(metadata.get("eventTime"), now),
            "createdAt": safe_int64(metadata.get("createdAt"), now),
            "text": text,
            "embedding": vector,
        }
        inserted_count = client.upsert(row)
        safe_log(f"User memory upsert succeeded, collection={client.collection_name}, sourceType={row['sourceType']}")
        return MemoryUpsertResponse(
            success=True,
            collection=client.collection_name,
            memory_id=memory_id,
            inserted_count=inserted_count,
        )
    except Exception as exc:
        safe_log(f"User memory upsert failed: {safe_error_message(exc)}")
        return MemoryUpsertResponse(success=False, collection=request.collection, error_message=safe_error_message(exc))


@app.post("/memory/search", response_model=MemorySearchResponse)
def search_memory(request: MemorySearchRequest) -> MemorySearchResponse:
    try:
        patient_id_hash = patient_hash_from_filter(request.filter)
        if not patient_id_hash:
            return MemorySearchResponse(
                success=False,
                collection=request.collection,
                error_message="patientIdHash filter is required",
            )
        query_vector = provider.encode_texts([request.query])[0] if provider is not None else []
        client = ensure_user_memory_client(request.collection)
        hits = client.search(query_vector, patient_id_hash, request.topK)
        results: list[MemorySearchResult] = []
        for hit in hits:
            entity = hit.get("entity") or hit
            metadata = {
                "patientIdHash": entity.get("patientIdHash"),
                "memoryLevel": entity.get("memoryLevel"),
                "sourceType": entity.get("sourceType"),
                "sourceId": entity.get("sourceId"),
                "department": entity.get("department"),
                "eventTime": entity.get("eventTime"),
                "createdAt": entity.get("createdAt"),
            }
            results.append(
                MemorySearchResult(
                    id=entity.get("memory_id"),
                    text=entity.get("text"),
                    score=float(hit.get("distance", hit.get("score", 0.0)) or 0.0),
                    metadata=metadata,
                )
            )
        safe_log(f"User memory search completed, collection={client.collection_name}, resultCount={len(results)}")
        return MemorySearchResponse(success=True, collection=client.collection_name, results=results)
    except Exception as exc:
        safe_log(f"User memory search failed: {safe_error_message(exc)}")
        return MemorySearchResponse(success=False, collection=request.collection, error_message=safe_error_message(exc))


@app.post("/memory/delete-by-source", response_model=MemoryDeleteResponse)
def delete_memory_by_source(request: MemoryDeleteBySourceRequest) -> MemoryDeleteResponse:
    try:
        patient_id_hash = patient_hash_from_filter(request.filter)
        if not patient_id_hash:
            return MemoryDeleteResponse(
                success=False,
                collection=request.collection,
                error_message="patientIdHash filter is required",
            )
        client = ensure_user_memory_client(request.collection)
        deleted_count = client.delete_by_source(patient_id_hash, request.sourceId, request.sourceType)
        safe_log(f"User memory delete-by-source completed, collection={client.collection_name}, deletedCount={deleted_count}")
        return MemoryDeleteResponse(success=True, collection=client.collection_name, deleted_count=deleted_count)
    except Exception as exc:
        safe_log(f"User memory delete-by-source failed: {safe_error_message(exc)}")
        return MemoryDeleteResponse(success=False, collection=request.collection, error_message=safe_error_message(exc))


@app.get("/memory/health", response_model=MemoryHealthResponse)
def memory_health() -> MemoryHealthResponse:
    collection = USER_MEMORY_COLLECTION_NAME
    try:
        client = UserMemoryMilvus(MILVUS_HOST, MILVUS_PORT, collection)
        client.connect()
        exists = client.has_collection()
        return MemoryHealthResponse(success=True, collection=collection, collection_exists=exists)
    except Exception as exc:
        return MemoryHealthResponse(
            success=False,
            collection=collection,
            collection_exists=False,
            error_message=safe_error_message(exc),
        )


def main() -> None:
    uvicorn.run("rag.rag_api_server:app", host="0.0.0.0", port=18080, reload=False)


if __name__ == "__main__":
    main()
