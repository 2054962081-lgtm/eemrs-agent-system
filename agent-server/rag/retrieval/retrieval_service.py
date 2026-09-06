"""Behavior-preserving retrieval orchestration for Medical RAG V1."""

from __future__ import annotations

import json
import re
import time
from collections import Counter, OrderedDict
from typing import Any

from rag.embedding_provider import EmbeddingProvider
from rag.rag_config import RAG_RETRIEVAL_MODE, RAG_RRF_K
from rag.schema.retrieval_models import RetrieveChunk, RetrieveRequest, RetrieveResponse
from rag.store.milvus_store import MilvusStore

from .fusion import fused_candidate_trace
from .hybrid_retriever import HybridRetriever
from .query_expansion import expand_medical_query
from .reranker import CrossEncoderReranker, default_reranker_info, get_default_reranker
from .scene_policy import doc_type_limits, retrieval_candidate_limit, selection_doc_type_caps
from .sparse_retriever import SparseRetriever


def clip_text(text: Any, limit: int = 1600) -> str:
    value = str(text or "")
    return value if len(value) <= limit else value[:limit] + "..."


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


TOPIC_KEYWORDS: dict[str, list[str]] = {
    "abdominal": ["腹痛", "肚子痛", "肚痛", "腹胀", "上腹痛", "下腹痛", "胃痛", "胃疼", "腹部"],
    "chest": ["胸痛", "胸闷", "心前区", "心绞痛", "大汗", "放射痛"],
    "urinary": ["尿频", "尿急", "尿痛", "血尿", "排尿", "小便", "腰痛", "尿路", "泌尿"],
    "cough": ["咳嗽", "咳痰", "咯血", "喘息", "气短", "呼吸困难", "呼吸道"],
    "fever": ["发热", "发烧", "高热", "体温", "寒战"],
    "rash": ["皮疹", "红疹", "紫癜", "过敏"],
    "neuro": ["偏瘫", "口角", "说话不清", "视物", "卒中", "头晕"],
    "bleeding_gi": ["呕血", "黑便", "便血", "消化道出血"],
    "mental": ["心理困扰", "心理", "焦虑", "抑郁", "绝望", "自伤", "自杀", "不想活", "幻听", "精神"],
    "palpitation": ["心悸", "心慌", "心跳快", "心跳很快", "心律", "漏跳"],
}

TOPIC_COMPATIBLE_WITH: dict[str, set[str]] = {
    "abdominal": {"bleeding_gi"},
    "bleeding_gi": {"abdominal"},
    "palpitation": {"chest", "neuro"},
    "chest": {"palpitation"},
    "neuro": {"palpitation"},
}

POPULATION_KEYWORDS: dict[str, list[str]] = {
    "pregnancy": ["怀孕", "孕妇", "孕期", "孕周", "胎动", "产后", "孕产妇"],
    "child": ["宝宝", "孩子", "儿童", "小孩", "婴儿", "幼儿", "女儿", "儿子", "娃"],
    "elderly": ["老人", "老年", "高龄", "我爸", "我妈", "父亲", "母亲"],
    "chemo": ["化疗", "肿瘤", "癌", "免疫抑制", "免疫低下", "移植", "白细胞", "中性粒细胞"],
    "anticoagulant": ["抗凝", "华法林", "利伐沙班", "阿司匹林"],
    "cardiac": ["冠心病", "心梗", "心绞痛"],
    "renal": ["肾功能不全", "肾病", "肾衰"],
    "diabetes": ["糖尿病", "血糖"],
    "hypertension": ["高血压", "血压"],
    "adult": ["成人", "adult"],
}

GENERAL_POPULATIONS = {"成人", "老年人", "儿童"}
SPECIAL_POPULATION_ALIASES: dict[str, set[str]] = {
    "pregnancy": {"孕妇", "产妇", "孕产妇"},
    "child": {"儿童", "婴儿", "幼儿"},
    "elderly": {"老年人"},
    "chemo": {"化疗患者", "肿瘤患者", "免疫低下"},
    "anticoagulant": {"抗凝药使用者"},
    "cardiac": {"冠心病患者"},
    "renal": {"肾功能不全患者"},
    "diabetes": {"糖尿病患者"},
    "hypertension": {"高血压患者"},
    "adult": {"成人"},
}
POPULATION_DOC_HINTS: dict[str, list[str]] = {
    "pregnancy": ["PREGNANCY", "孕", "产妇", "胎动"],
    "chemo": ["CHEMO", "IMMUNO", "免疫", "化疗", "肿瘤", "免疫低下"],
    "child": ["PEDIATRIC", "CHILD", "儿童", "小儿", "婴幼儿"],
    "diabetes": ["DIABETES", "糖尿病", "血糖"],
    "hypertension": ["HYPERTENSION", "高血压", "血压"],
}

QUERY_NEGATION_PREFIXES = ["否认", "无", "没有", "未", "不"]
EXPLICIT_RED_FLAG_KEYWORDS = [
    "自杀", "自伤", "不想活", "伤害自己", "伤害他人", "服用大量药物", "幻听",
    "胸痛", "胸闷", "晕厥", "近晕厥", "大汗", "呼吸困难", "口唇发紫", "意识改变",
]


def text_has_keyword(text: str, keyword: str) -> bool:
    if not keyword:
        return False
    index = text.find(keyword)
    while index >= 0:
        prefix = text[max(0, index - 4): index]
        if not any(prefix.endswith(negation) for negation in QUERY_NEGATION_PREFIXES):
            return True
        index = text.find(keyword, index + len(keyword))
    return False


def detect_labels(text: str, vocabulary: dict[str, list[str]]) -> set[str]:
    source = str(text or "")
    labels: set[str] = set()
    for label, keywords in vocabulary.items():
        if any(text_has_keyword(source, keyword) for keyword in keywords):
            labels.add(label)
    return labels


def entity_text(entity: dict[str, Any], include_chunk_text: bool = False) -> str:
    values = [
        entity.get("chunk_id"),
        entity.get("doc_id"),
        entity.get("doc_type"),
        entity.get("title"),
        entity.get("related_symptoms"),
        entity.get("applicable_population"),
        entity.get("related_departments"),
    ]
    if include_chunk_text:
        values.append(entity.get("chunk_text"))
    return " ".join(str(value or "") for value in values)


def detect_entity_topics(entity: dict[str, Any]) -> set[str]:
    topics = detect_labels(entity_text(entity, include_chunk_text=False), TOPIC_KEYWORDS)
    related = str(entity.get("related_symptoms") or "")
    if "黑便" in related or "便血" in related or "呕血" in related:
        topics.add("bleeding_gi")
    return topics


def detect_entity_populations(entity: dict[str, Any]) -> set[str]:
    text = entity_text(entity, include_chunk_text=False)
    populations = detect_labels(text, POPULATION_KEYWORDS)
    for label, hints in POPULATION_DOC_HINTS.items():
        if any(hint in text for hint in hints):
            populations.add(label)
    applicable = str(entity.get("applicable_population") or "")
    for label, aliases in SPECIAL_POPULATION_ALIASES.items():
        if any(alias in applicable for alias in aliases):
            populations.add(label)
    return populations


def compatible_topics(primary_topics: set[str], chunk_topics: set[str]) -> bool:
    if not primary_topics or not chunk_topics:
        return True
    if primary_topics & chunk_topics:
        return True
    for topic in primary_topics:
        if TOPIC_COMPATIBLE_WITH.get(topic, set()) & chunk_topics:
            return True
    return False


def topic_alignment(primary_topics: set[str], chunk_topics: set[str]) -> str:
    if not primary_topics or not chunk_topics:
        return "neutral"
    if primary_topics & chunk_topics:
        return "match"
    for topic in primary_topics:
        if TOPIC_COMPATIBLE_WITH.get(topic, set()) & chunk_topics:
            return "compatible"
    return "mismatch"


def inactive_special_population(active_populations: set[str], chunk_populations: set[str]) -> set[str]:
    gated = chunk_populations - {"elderly"}
    return gated - active_populations


def population_gate_labels(entity: dict[str, Any], chunk_populations: set[str]) -> set[str]:
    doc_type = str(entity.get("doc_type") or "")
    identity_text = " ".join(str(entity.get(key) or "") for key in ["chunk_id", "doc_id", "title"])
    if doc_type == "special_population":
        return chunk_populations - {"elderly"}
    gated: set[str] = set()
    for label, hints in POPULATION_DOC_HINTS.items():
        if label in chunk_populations and any(hint in identity_text for hint in hints):
            gated.add(label)
    applicable = {
        item.strip()
        for item in re.split(r"[;；,，、\s]+", str(entity.get("applicable_population") or ""))
        if item.strip()
    }
    if applicable and not (applicable & GENERAL_POPULATIONS):
        gated.update(chunk_populations - {"elderly", "child"})
    return gated


def has_explicit_red_flag_evidence(query: str) -> bool:
    return any(text_has_keyword(query, keyword) for keyword in EXPLICIT_RED_FLAG_KEYWORDS)


def select_retrieval_chunks(
    query: str,
    candidates: list[tuple[float, dict[str, Any]]],
    scene: str,
    include_doc_types: list[str] | None,
    top_k: int,
) -> tuple[list[tuple[float, float, dict[str, Any], str, str]], dict[str, Any]]:
    primary_topics = detect_labels(query, TOPIC_KEYWORDS)
    active_populations = detect_labels(query, POPULATION_KEYWORDS)
    explicit_red_flag_evidence = has_explicit_red_flag_evidence(query)
    caps = selection_doc_type_caps(scene, include_doc_types, top_k)
    best_score = max((score for score, _ in candidates), default=0.0)
    min_relative_score = best_score * 0.50 if best_score > 0 else 0.0
    filtered_by_reason: Counter[str] = Counter()
    inspected: list[dict[str, Any]] = []
    eligible: list[tuple[float, float, dict[str, Any], str, str]] = []

    for score, entity in candidates:
        doc_type = str(entity.get("doc_type") or "unknown")
        chunk_topics = detect_entity_topics(entity)
        chunk_populations = detect_entity_populations(entity)
        alignment = topic_alignment(primary_topics, chunk_topics)
        reason = "TOPIC_MATCH" if alignment == "match" else alignment.upper()
        red_flag_match = doc_type == "red_flag" and explicit_red_flag_evidence and alignment in {"match", "compatible"}
        filter_reason: str | None = None

        gated_populations = population_gate_labels(entity, chunk_populations)
        inactive_populations = inactive_special_population(active_populations, gated_populations)
        if red_flag_match:
            reason = "EXPLICIT_RED_FLAG_MATCH"
        elif doc_type == "special_population" and not (active_populations & chunk_populations):
            filter_reason = "INACTIVE_SPECIAL_POPULATION"
        elif inactive_populations:
            filter_reason = "INACTIVE_SPECIAL_POPULATION"
        elif doc_type in {"red_flag", "department_triage", "symptom_inquiry", "medical_record_template"} and not compatible_topics(primary_topics, chunk_topics):
            filter_reason = "TOPIC_MISMATCH"
        elif primary_topics and alignment == "neutral" and doc_type in {"red_flag", "department_triage", "symptom_inquiry", "medical_record_template"}:
            filter_reason = "TOPIC_MISMATCH"
        elif alignment == "neutral" and best_score > 0 and score < min_relative_score:
            filter_reason = "LOW_RELATIVE_SCORE"

        final_score = score
        if red_flag_match:
            final_score += 0.30
        elif alignment == "match":
            final_score += 0.15
        elif alignment == "compatible":
            final_score += 0.08
        elif alignment == "mismatch":
            final_score -= 0.35
        if active_populations & chunk_populations:
            final_score += 0.08

        inspected.append({
            "chunk_id": entity.get("chunk_id"),
            "doc_type": doc_type,
            "semantic_score": round(score, 6),
            "final_score": round(final_score, 6),
            "topic_alignment": alignment,
            "chunk_topics": sorted(chunk_topics),
            "chunk_populations": sorted(chunk_populations),
            "filtered_reason": filter_reason,
        })
        if filter_reason:
            filtered_by_reason[filter_reason] += 1
            continue
        eligible.append((final_score, score, entity, alignment, reason))

    eligible.sort(key=lambda item: (-item[0], -item[1], str(item[2].get("chunk_id") or "")))
    final_doc_type_counts: Counter[str] = Counter()
    selected: list[tuple[float, float, dict[str, Any], str, str]] = []
    for final_score, score, entity, alignment, reason in eligible:
        doc_type = str(entity.get("doc_type") or "unknown")
        cap = caps.get(doc_type, top_k)
        if final_doc_type_counts[doc_type] >= cap:
            filtered_by_reason["DOC_TYPE_CAP"] += 1
            continue
        selected.append((final_score, score, entity, alignment, reason))
        final_doc_type_counts[doc_type] += 1
        if len(selected) >= top_k:
            break

    diagnostics = {
        "candidate_count": len(candidates),
        "final_count": len(selected),
        "primary_topics": sorted(primary_topics),
        "active_populations": sorted(active_populations),
        "filtered_count": sum(filtered_by_reason.values()),
        "filtered_by_reason": dict(filtered_by_reason),
        "final_doc_type_counts": dict(final_doc_type_counts),
        "final_chunks": [
            {
                "rank": index,
                "chunk_id": entity.get("chunk_id"),
                "doc_type": entity.get("doc_type"),
                "semantic_score": round(score, 6),
                "final_score": round(final_score, 6),
                "topic_alignment": alignment,
                "selection_reason": reason,
            }
            for index, (final_score, score, entity, alignment, reason) in enumerate(selected, 1)
        ],
        "inspected_candidates": inspected,
    }
    return selected, diagnostics


class RetrievalService:
    def __init__(
        self,
        provider: EmbeddingProvider,
        store: MilvusStore,
        retrieval_mode: str = RAG_RETRIEVAL_MODE,
        rrf_k: int = RAG_RRF_K,
    ):
        self.provider = provider
        self.store = store
        self.retrieval_mode = retrieval_mode
        self.rrf_k = rrf_k

    def retrieve(self, request: RetrieveRequest, trace_meta: dict[str, Any]) -> RetrieveResponse:
        try:
            expanded_query = expand_medical_query(request.query, request.scene)
            if self.retrieval_mode == "hybrid_rrf":
                return self._retrieve_hybrid(request, trace_meta, expanded_query)
            if self.retrieval_mode == "hybrid_rerank":
                return self._retrieve_hybrid(request, trace_meta, expanded_query, reranker=get_default_reranker())
            if self.retrieval_mode == "bm25":
                return self._retrieve_bm25(request, trace_meta, expanded_query)
            return self._retrieve_dense(request, trace_meta, expanded_query)
        except Exception as exc:
            return RetrieveResponse(success=False, query=request.query, error_message=str(exc))

    def _retrieve_dense(
        self,
        request: RetrieveRequest,
        trace_meta: dict[str, Any],
        expanded_query: str,
    ) -> RetrieveResponse:
        query_vector = self.provider.encode_texts([expanded_query])[0]
        merged: OrderedDict[str, tuple[float, dict[str, Any]]] = OrderedDict()
        for doc_type, limit in doc_type_limits(request.scene, request.include_doc_types, request.top_k).items():
            hits = self.store.search_dense(
                query_vector,
                top_k=retrieval_candidate_limit(limit, request.top_k),
                doc_type=doc_type,
            )
            for hit in hits:
                entity = hit.get("entity") or hit
                chunk_id = entity.get("chunk_id")
                if not chunk_id:
                    continue
                score = float(hit.get("distance", hit.get("score", 0.0)) or 0.0)
                previous = merged.get(chunk_id)
                if previous is None or score > previous[0]:
                    merged[chunk_id] = (score, entity)

        sorted_hits, selection_meta = select_retrieval_chunks(
            request.query,
            list(merged.values()),
            request.scene,
            request.include_doc_types,
            request.top_k,
        )
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
                final_score=final_score,
                topic_alignment=alignment,
                selection_reason=reason,
                chunk_text=clip_text(entity.get("chunk_text")),
            )
            for final_score, score, entity, alignment, reason in sorted_hits
        ]
        return RetrieveResponse(
            success=True,
            query=request.query,
            expanded_query=expanded_query,
            doc_type_counts=dict(Counter(chunk.doc_type or "unknown" for chunk in chunks)),
            used_query_expansion=expanded_query != request.query,
            chunks=chunks,
            trace_meta={
                **trace_meta,
                "result_count": len(chunks),
                "doc_type_counts": dict(Counter(chunk.doc_type or "unknown" for chunk in chunks)),
                "retrieval_selection": selection_meta,
            },
        )

    def _retrieve_hybrid(
        self,
        request: RetrieveRequest,
        trace_meta: dict[str, Any],
        expanded_query: str,
        reranker: CrossEncoderReranker | None = None,
    ) -> RetrieveResponse:
        total_started = time.perf_counter()
        query_vector = self.provider.encode_texts([expanded_query])[0]
        hybrid = HybridRetriever(self.store, rrf_k=self.rrf_k)
        merged: OrderedDict[str, tuple[float, dict[str, Any]]] = OrderedDict()
        doc_type_traces: list[dict[str, Any]] = []
        mode = "hybrid_rerank" if reranker else "hybrid_rrf"
        for doc_type, limit in doc_type_limits(request.scene, request.include_doc_types, request.top_k).items():
            result = hybrid.search_doc_type(
                query_vector,
                request.query,
                top_k=retrieval_candidate_limit(limit, request.top_k),
                doc_type=doc_type,
            )
            fused_candidates = result.fused_candidates
            if reranker:
                rerank_started = time.perf_counter()
                fused_candidates = reranker.rerank(request.query, fused_candidates)
                result.trace["latency"]["rerank_ms"] = int((time.perf_counter() - rerank_started) * 1000)
                result.trace["rerank_candidates"] = [
                    {
                        **fused_candidate_trace(candidate, rank),
                        "rrf_rank": candidate.entity.get("_rrf_rank"),
                        "rerank_rank": candidate.entity.get("_rerank_rank"),
                        "rerank_score": candidate.entity.get("_rerank_score"),
                    }
                    for rank, candidate in enumerate(fused_candidates, 1)
                ]
            doc_type_traces.append(result.trace)
            for fused in fused_candidates:
                entity = dict(fused.entity)
                entity["_dense_score"] = fused.dense_score
                entity["_sparse_score"] = fused.sparse_score
                entity["_rrf_score"] = fused.rrf_score
                entity["_dense_rank"] = fused.dense_rank
                entity["_sparse_rank"] = fused.sparse_rank
                entity["_retrieval_channels"] = list(fused.retrieval_channels)
                entity["_public_score"] = fused.dense_score
                if reranker:
                    entity["_rrf_rank"] = fused.entity.get("_rrf_rank")
                    entity["_rerank_rank"] = fused.entity.get("_rerank_rank")
                    entity["_rerank_score"] = fused.entity.get("_rerank_score")
                previous = merged.get(fused.chunk_id)
                selection_score = float(entity["_rerank_score"]) if reranker else fused.rrf_score
                if previous is None or selection_score > previous[0]:
                    merged[fused.chunk_id] = (selection_score, entity)

        sorted_hits, selection_meta = select_retrieval_chunks(
            request.query,
            list(merged.values()),
            request.scene,
            request.include_doc_types,
            request.top_k,
        )
        selected_candidates = [
            {
                "rank": index,
                "chunk_id": entity.get("chunk_id"),
                "doc_id": entity.get("doc_id"),
                "doc_type": entity.get("doc_type"),
                "title": entity.get("title"),
                "selection_score": score,
                "final_score": final_score,
                "dense_score": entity.get("_dense_score"),
                "sparse_score": entity.get("_sparse_score"),
                "rrf_score": entity.get("_rrf_score"),
                "rrf_rank": entity.get("_rrf_rank"),
                "rerank_rank": entity.get("_rerank_rank"),
                "rerank_score": entity.get("_rerank_score"),
                "retrieval_channels": entity.get("_retrieval_channels", []),
            }
            for index, (final_score, score, entity, _alignment, _reason) in enumerate(sorted_hits, 1)
        ]
        chunks = self._chunks_from_selected(sorted_hits)
        hybrid_trace = {
            "query_original": request.query,
            "query_expanded": expanded_query,
            "dense_query": expanded_query,
            "sparse_query": request.query,
            "reranker_query": request.query if reranker else None,
            "retrieval_mode": mode,
            "rrf_k": self.rrf_k,
            "reranker": default_reranker_info() if reranker else {"enabled": False},
            "doc_type_traces": doc_type_traces,
            "selected_candidates": selected_candidates,
            "latency": {
                "total_ms": int((time.perf_counter() - total_started) * 1000),
                "dense_ms": sum(item.get("latency", {}).get("dense_ms", 0) for item in doc_type_traces),
                "sparse_ms": sum(item.get("latency", {}).get("sparse_ms", 0) for item in doc_type_traces),
                "fusion_ms": sum(item.get("latency", {}).get("fusion_ms", 0) for item in doc_type_traces),
                "rerank_ms": sum(item.get("latency", {}).get("rerank_ms", 0) for item in doc_type_traces),
            },
        }
        return RetrieveResponse(
            success=True,
            query=request.query,
            expanded_query=expanded_query,
            doc_type_counts=dict(Counter(chunk.doc_type or "unknown" for chunk in chunks)),
            used_query_expansion=expanded_query != request.query,
            chunks=chunks,
            trace_meta={
                **trace_meta,
                "result_count": len(chunks),
                "doc_type_counts": dict(Counter(chunk.doc_type or "unknown" for chunk in chunks)),
                "retrieval_selection": selection_meta,
                "hybrid_retrieval": hybrid_trace,
            },
        )

    def _retrieve_bm25(
        self,
        request: RetrieveRequest,
        trace_meta: dict[str, Any],
        expanded_query: str,
    ) -> RetrieveResponse:
        total_started = time.perf_counter()
        sparse = SparseRetriever(self.store)
        merged: OrderedDict[str, tuple[float, dict[str, Any]]] = OrderedDict()
        sparse_traces: list[dict[str, Any]] = []
        for doc_type, limit in doc_type_limits(request.scene, request.include_doc_types, request.top_k).items():
            started = time.perf_counter()
            candidates = sparse.search(request.query, top_k=retrieval_candidate_limit(limit, request.top_k), doc_type=doc_type)
            sparse_traces.append({
                "doc_type": doc_type,
                "sparse_candidates": [
                    {"rank": item.rank, "chunk_id": item.chunk_id, "score": item.score}
                    for item in candidates
                ],
                "latency": {"sparse_ms": int((time.perf_counter() - started) * 1000)},
            })
            for candidate in candidates:
                entity = dict(candidate.entity)
                entity["_sparse_score"] = candidate.score
                entity["_sparse_rank"] = candidate.rank
                entity["_retrieval_channels"] = ["bm25"]
                entity["_public_score"] = None
                previous = merged.get(candidate.chunk_id)
                score = float(candidate.score or 0.0)
                if previous is None or score > previous[0]:
                    merged[candidate.chunk_id] = (score, entity)

        sorted_hits, selection_meta = select_retrieval_chunks(
            request.query,
            list(merged.values()),
            request.scene,
            request.include_doc_types,
            request.top_k,
        )
        chunks = self._chunks_from_selected(sorted_hits)
        return RetrieveResponse(
            success=True,
            query=request.query,
            expanded_query=expanded_query,
            doc_type_counts=dict(Counter(chunk.doc_type or "unknown" for chunk in chunks)),
            used_query_expansion=expanded_query != request.query,
            chunks=chunks,
            trace_meta={
                **trace_meta,
                "result_count": len(chunks),
                "doc_type_counts": dict(Counter(chunk.doc_type or "unknown" for chunk in chunks)),
                "retrieval_selection": selection_meta,
                "bm25_retrieval": {
                    "query_original": request.query,
                    "query_expanded": expanded_query,
                    "dense_query": None,
                    "sparse_query": request.query,
                    "retrieval_mode": "bm25",
                    "doc_type_traces": sparse_traces,
                    "latency": {
                        "total_ms": int((time.perf_counter() - total_started) * 1000),
                        "sparse_ms": sum(item.get("latency", {}).get("sparse_ms", 0) for item in sparse_traces),
                    },
                },
            },
        )

    def _chunks_from_selected(
        self,
        sorted_hits: list[tuple[float, float, dict[str, Any], str, str]],
    ) -> list[RetrieveChunk]:
        return [
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
                score=entity.get("_public_score", score),
                final_score=final_score,
                topic_alignment=alignment,
                selection_reason=reason,
                chunk_text=clip_text(entity.get("chunk_text")),
            )
            for final_score, score, entity, alignment, reason in sorted_hits
        ]
