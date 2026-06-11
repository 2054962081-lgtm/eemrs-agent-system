"""FastAPI wrapper for local medical RAG retrieval.

Run:
    python -m rag.rag_api_server
    uvicorn rag.rag_api_server:app --host 0.0.0.0 --port 18080
"""

from __future__ import annotations

import json
import re
import time
import uuid
from collections import Counter, OrderedDict
from typing import Any

import uvicorn
from fastapi import FastAPI
from pydantic import BaseModel, Field

from .embedding_provider import EmbeddingProvider
from .milvus_client import MedicalRagMilvus, UserMemoryMilvus
from .rag_config import DEFAULT_TOP_K, MILVUS_COLLECTION_NAME, MILVUS_HOST, MILVUS_PORT, USER_MEMORY_COLLECTION_NAME
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
    applicable_population: str | None = None
    related_symptoms: str | None = None
    must_ask: list[str] = []
    red_flags: list[str] = []
    forbidden_actions: list[str] = []
    expected_response_points: list[str] = []
    doctor_record_fields: list[str] = []
    score: float | None = None
    chunk_text: str | None = None


class RetrieveResponse(BaseModel):
    success: bool
    query: str
    expanded_query: str | None = None
    doc_type_counts: dict[str, int] = {}
    used_query_expansion: bool = False
    chunks: list[RetrieveChunk] = []
    error_message: str | None = None


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


def clip_text(text: Any, limit: int = 1600) -> str:
    value = str(text or "")
    return value if len(value) <= limit else value[:limit] + "..."


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


def as_list(value: Any) -> list[str]:
    if value is None:
        return []
    if isinstance(value, list):
        result: list[str] = []
        for item in value:
            result.extend(as_list(item))
        return list(dict.fromkeys(item.strip() for item in result if item and item.strip()))
    if isinstance(value, dict):
        result = []
        for item in value.values():
            result.extend(as_list(item))
        return list(dict.fromkeys(result))
    text = str(value).strip()
    if not text:
        return []
    parts = re.split(r"[;；\n\r]+", text)
    return [part.strip() for part in parts if part.strip()]


def parse_content_json(value: Any) -> dict[str, Any]:
    if isinstance(value, dict):
        return value
    if not value:
        return {}
    try:
        parsed = json.loads(str(value))
        return parsed if isinstance(parsed, dict) else {}
    except json.JSONDecodeError:
        return {}


def structured_fields(entity: dict[str, Any]) -> dict[str, list[str]]:
    content = parse_content_json(entity.get("content_json"))
    return {
        "must_ask": as_list(content.get("must_ask")),
        "red_flags": as_list(content.get("red_flags")),
        "forbidden_actions": as_list(content.get("forbidden_actions")),
        "expected_response_points": as_list(content.get("expected_response_points")),
        "doctor_record_fields": as_list(content.get("doctor_record_fields")),
    }


def expand_medical_query(query: str, scene: str) -> str:
    text = str(query or "").strip()
    additions: list[str] = []
    rules = [
        (["宝宝", "孩子", "儿童", "小孩", "婴儿", "幼儿", "女儿", "儿子", "娃"], ["儿童", "年龄", "体温", "精神状态", "呼吸情况", "吃奶饮水", "尿量", "抽搐"]),
        (["老人", "老年", "我爸", "我妈", "父亲", "母亲"], ["老年人", "症状不典型", "心梗", "卒中", "感染", "跌倒骨折", "意识改变"]),
        (["怀孕", "孕", "产后", "胎动", "恶露"], ["孕产妇", "孕周", "胎动", "阴道出血流液", "血压", "水肿", "子痫前期"]),
        (["糖尿病", "血糖"], ["低血糖", "高血糖危象", "出汗心慌手抖", "口渴多尿", "酮症酸中毒"]),
        (["高血压", "血压"], ["高血压急症", "头痛", "胸痛", "神经系统症状", "靶器官损害"]),
        (["抗凝", "华法林", "阿司匹林", "利伐沙班", "黑便"], ["抗凝用药", "出血风险", "黑便呕血", "头外伤", "颅内出血"]),
        (["胸痛", "胸闷", "冒冷汗"], ["急性冠脉综合征", "放射痛", "大汗", "呼吸困难", "急诊"]),
        (["偏瘫", "口角", "说话不清", "视物", "头晕"], ["卒中", "TIA", "FAST", "发病时间", "120"]),
        (["喘", "呼吸困难", "哮喘", "慢阻肺"], ["呼吸困难", "喘息", "血氧", "三凹征", "急性发作"]),
        (["发烧", "发热", "高热", "体温"], ["发热", "体温", "持续时间", "精神状态", "寒战", "皮疹", "感染"]),
        (["皮疹", "红疹", "紫癜"], ["皮疹", "发热", "皮疹形态", "按压褪色", "过敏", "严重药疹"]),
        (["尿痛", "小便疼", "尿频", "尿急"], ["泌尿系统", "尿痛尿频", "发热", "腰痛", "尿量", "尿路感染"]),
        (["误服", "吃了几片", "中毒", "药物"], ["误服药物", "药物名称", "剂量数量", "服用时间", "意识状态", "中毒急诊"]),
        (["不想活", "自杀", "绝望", "大量药", "幻听"], ["自伤自杀风险", "立即陪伴", "急诊", "120", "危机干预"]),
        (["化疗", "免疫抑制", "移植", "术后"], ["免疫低下", "感染风险", "发热", "伤口红肿渗液", "尽快就医"]),
    ]
    for triggers, terms in rules:
        if any(trigger in text for trigger in triggers):
            additions.extend(terms)
    if scene == "deep_inquiry":
        additions.extend(["病程", "诱因", "伴随症状", "既往史", "用药史", "过敏史", "红旗信号"])
    additions = [item for item in dict.fromkeys(additions) if item not in text]
    expanded = " ".join([text, *additions]).strip()
    return expanded[:600]


def quota_for_scene(scene: str) -> OrderedDict[str, int]:
    quotas = {
        "pre_inquiry": OrderedDict([
            ("red_flag", 2),
            ("symptom_inquiry", 2),
            ("special_population", 2),
            ("department_triage", 1),
            ("medical_record_template", 1),
        ]),
        "deep_inquiry": OrderedDict([
            ("red_flag", 2),
            ("symptom_inquiry", 3),
            ("special_population", 2),
            ("department_triage", 1),
            ("medical_record_template", 2),
        ]),
        "medical_record": OrderedDict([
            ("medical_record_template", 3),
            ("symptom_inquiry", 2),
            ("special_population", 2),
            ("red_flag", 1),
            ("department_triage", 1),
        ]),
    }
    return quotas.get(scene, quotas["pre_inquiry"])


def ordered_doc_types(scene: str, include_doc_types: list[str] | None) -> list[str]:
    preferred = MEDICAL_RECORD_DOC_TYPES if scene == "medical_record" else DEFAULT_DOC_TYPES
    requested = include_doc_types or preferred
    requested_set = [doc_type for doc_type in requested if doc_type in ALLOWED_DOC_TYPES]
    ordered = [doc_type for doc_type in preferred if doc_type in requested_set]
    ordered.extend(doc_type for doc_type in requested_set if doc_type not in ordered)
    return ordered or preferred


def doc_type_limits(scene: str, include_doc_types: list[str] | None, top_k: int) -> OrderedDict[str, int]:
    allowed = set(ordered_doc_types(scene, include_doc_types))
    limits = OrderedDict((doc_type, limit) for doc_type, limit in quota_for_scene(scene).items() if doc_type in allowed)
    for doc_type in allowed:
        limits.setdefault(doc_type, max(1, min(2, top_k)))
    return limits


@app.on_event("startup")
def startup() -> None:
    global provider, milvus
    provider = EmbeddingProvider()
    milvus = MedicalRagMilvus(MILVUS_HOST, MILVUS_PORT, MILVUS_COLLECTION_NAME)
    try:
        milvus.connect()
        if not milvus.has_collection():
            safe_log(f"Warning: RAG collection missing: {MILVUS_COLLECTION_NAME}")
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
        "collection": MILVUS_COLLECTION_NAME,
        "collection_exists": exists,
        "user_memory_collection": USER_MEMORY_COLLECTION_NAME,
        "user_memory_collection_exists": user_memory_exists,
    }


@app.post("/rag/retrieve", response_model=RetrieveResponse)
def retrieve(request: RetrieveRequest) -> RetrieveResponse:
    if provider is None or milvus is None:
        return RetrieveResponse(success=False, query=request.query, error_message="RAG 服务尚未连接 Milvus")
    if not milvus.has_collection():
        return RetrieveResponse(success=False, query=request.query, error_message=f"collection 不存在: {milvus.collection_name}")

    try:
        expanded_query = expand_medical_query(request.query, request.scene)
        query_vector = provider.encode_texts([expanded_query])[0]
        merged: OrderedDict[str, tuple[float, dict[str, Any]]] = OrderedDict()
        priority = {doc_type: idx for idx, doc_type in enumerate(doc_type_limits(request.scene, request.include_doc_types, request.top_k), 1)}
        for doc_type, limit in doc_type_limits(request.scene, request.include_doc_types, request.top_k).items():
            hits = milvus.search(query_vector, top_k=limit, doc_type=doc_type)
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
            key=lambda item: (priority.get(str(item[1].get("doc_type")), 99), -item[0]),
        )[: request.top_k]
        chunks = [
            RetrieveChunk(
                chunk_id=entity.get("chunk_id"),
                doc_id=entity.get("doc_id"),
                doc_type=entity.get("doc_type"),
                title=entity.get("title"),
                urgency_level=entity.get("urgency_level"),
                related_departments=entity.get("related_departments"),
                applicable_population=entity.get("applicable_population"),
                related_symptoms=entity.get("related_symptoms"),
                **structured_fields(entity),
                score=score,
                chunk_text=clip_text(entity.get("chunk_text")),
            )
            for score, entity in sorted_hits
        ]
        return RetrieveResponse(
            success=True,
            query=request.query,
            expanded_query=expanded_query,
            doc_type_counts=dict(Counter(chunk.doc_type or "unknown" for chunk in chunks)),
            used_query_expansion=expanded_query != request.query,
            chunks=chunks,
        )
    except Exception as exc:
        return RetrieveResponse(success=False, query=request.query, error_message=str(exc))


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
