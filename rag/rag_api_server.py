"""FastAPI wrapper for local medical RAG retrieval.

Run:
    python -m rag.rag_api_server
    uvicorn rag.rag_api_server:app --host 0.0.0.0 --port 18080
"""

from __future__ import annotations

from collections import OrderedDict
from typing import Any

import uvicorn
from fastapi import FastAPI
from pydantic import BaseModel, Field

from .embedding_provider import EmbeddingProvider
from .milvus_client import MedicalRagMilvus
from .rag_config import DEFAULT_TOP_K, MILVUS_COLLECTION_NAME, MILVUS_HOST, MILVUS_PORT
from .rag_schema import ALLOWED_DOC_TYPES


DEFAULT_DOC_TYPES = [
    "red_flag",
    "symptom_inquiry",
    "special_population",
    "department_triage",
    "medical_record_template",
]

MEDICAL_RECORD_DOC_TYPES = [
    "medical_record_template",
    "symptom_inquiry",
    "special_population",
    "red_flag",
    "department_triage",
]


class RetrieveRequest(BaseModel):
    query: str = Field(min_length=1)
    top_k: int = Field(default=DEFAULT_TOP_K, ge=1, le=30)
    include_doc_types: list[str] | None = None
    scene: str = "pre_inquiry"


class RetrieveChunk(BaseModel):
    chunk_id: str | None = None
    doc_id: str | None = None
    doc_type: str | None = None
    title: str | None = None
    urgency_level: str | None = None
    related_departments: str | None = None
    score: float | None = None
    chunk_text: str | None = None


class RetrieveResponse(BaseModel):
    success: bool
    query: str
    chunks: list[RetrieveChunk] = []
    error_message: str | None = None


app = FastAPI(title="Medical RAG Retrieval Service", version="1.0")
provider: EmbeddingProvider | None = None
milvus: MedicalRagMilvus | None = None


def clip_text(text: Any, limit: int = 1600) -> str:
    value = str(text or "")
    return value if len(value) <= limit else value[:limit] + "..."


def ordered_doc_types(scene: str, include_doc_types: list[str] | None) -> list[str]:
    preferred = MEDICAL_RECORD_DOC_TYPES if scene == "medical_record" else DEFAULT_DOC_TYPES
    requested = include_doc_types or preferred
    requested_set = [doc_type for doc_type in requested if doc_type in ALLOWED_DOC_TYPES]
    ordered = [doc_type for doc_type in preferred if doc_type in requested_set]
    ordered.extend(doc_type for doc_type in requested_set if doc_type not in ordered)
    return ordered or preferred


@app.on_event("startup")
def startup() -> None:
    global provider, milvus
    provider = EmbeddingProvider()
    milvus = MedicalRagMilvus(MILVUS_HOST, MILVUS_PORT, MILVUS_COLLECTION_NAME)
    milvus.connect()
    if not milvus.has_collection():
        raise RuntimeError(f"collection 不存在: {MILVUS_COLLECTION_NAME}")


@app.get("/health")
def health() -> dict[str, Any]:
    exists = bool(milvus and milvus.has_collection())
    return {"success": exists, "collection": MILVUS_COLLECTION_NAME, "collection_exists": exists}


@app.post("/rag/retrieve", response_model=RetrieveResponse)
def retrieve(request: RetrieveRequest) -> RetrieveResponse:
    if provider is None or milvus is None:
        return RetrieveResponse(success=False, query=request.query, error_message="RAG 服务尚未初始化")
    if not milvus.has_collection():
        return RetrieveResponse(success=False, query=request.query, error_message=f"collection 不存在: {milvus.collection_name}")

    try:
        query_vector = provider.encode_texts([request.query])[0]
        per_type_limit = max(2, min(3, request.top_k))
        merged: OrderedDict[str, tuple[float, dict[str, Any]]] = OrderedDict()
        for doc_type in ordered_doc_types(request.scene, request.include_doc_types):
            hits = milvus.search(query_vector, top_k=per_type_limit, doc_type=doc_type)
            for hit in hits:
                entity = hit.get("entity") or hit
                chunk_id = entity.get("chunk_id")
                if not chunk_id:
                    continue
                score = float(hit.get("distance", hit.get("score", 0.0)) or 0.0)
                previous = merged.get(chunk_id)
                if previous is None or score > previous[0]:
                    merged[chunk_id] = (score, entity)

        sorted_hits = sorted(
            merged.values(),
            key=lambda item: item[0],
            reverse=True,
        )[: request.top_k]
        chunks = [
            RetrieveChunk(
                chunk_id=entity.get("chunk_id"),
                doc_id=entity.get("doc_id"),
                doc_type=entity.get("doc_type"),
                title=entity.get("title"),
                urgency_level=entity.get("urgency_level"),
                related_departments=entity.get("related_departments"),
                score=score,
                chunk_text=clip_text(entity.get("chunk_text")),
            )
            for score, entity in sorted_hits
        ]
        return RetrieveResponse(success=True, query=request.query, chunks=chunks)
    except Exception as exc:
        return RetrieveResponse(success=False, query=request.query, error_message=str(exc))


def main() -> None:
    uvicorn.run("rag.rag_api_server:app", host="0.0.0.0", port=18080, reload=False)


if __name__ == "__main__":
    main()
