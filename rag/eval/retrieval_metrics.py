"""Shared retrieval relevance matching and metric computation."""

from __future__ import annotations

import math
from dataclasses import dataclass
from typing import Any


GOLD_FIELD = "rag_ground_truth.expected_knowledge_topics"
MATCH_FIELD = "doc_id"


@dataclass(frozen=True)
class CaseMetric:
    case_id: str
    gold_labels: list[str]
    retrieved_top10: list[dict[str, Any]]
    relevance_vector_top10: list[int]
    gold_count: int
    retrieved_gold_count_at_5: int
    retrieved_gold_count_at_10: int
    hit_at_5: bool
    recall_at_5: float
    recall_at_10: float
    first_relevant_rank: int | None
    reciprocal_rank: float
    dcg_at_5: float
    idcg_at_5: float
    ndcg_at_5: float
    labeled: bool

    def to_dict(self) -> dict[str, Any]:
        return {
            "case_id": self.case_id,
            "gold_labels": self.gold_labels,
            "retrieved_top10": self.retrieved_top10,
            "relevance_vector_top10": self.relevance_vector_top10,
            "gold_count": self.gold_count,
            "retrieved_gold_count_at_5": self.retrieved_gold_count_at_5,
            "retrieved_gold_count_at_10": self.retrieved_gold_count_at_10,
            "hit_at_5": self.hit_at_5,
            "recall_at_5": self.recall_at_5,
            "recall_at_10": self.recall_at_10,
            "first_relevant_rank": self.first_relevant_rank,
            "reciprocal_rank": self.reciprocal_rank,
            "dcg_at_5": self.dcg_at_5,
            "idcg_at_5": self.idcg_at_5,
            "ndcg_at_5": self.ndcg_at_5,
            "labeled": self.labeled,
        }


def normalize_gold_labels(labels: Any) -> list[str]:
    if not labels:
        return []
    if isinstance(labels, (str, int, float)):
        labels = [labels]
    normalized: list[str] = []
    for label in labels:
        value = str(label or "").strip()
        if value and value not in normalized:
            normalized.append(value)
    return normalized


def item_match_value(retrieved_item: dict[str, Any]) -> str:
    return str(retrieved_item.get(MATCH_FIELD) or "").strip()


def match_relevance(retrieved_item: dict[str, Any], gold_labels: list[str]) -> bool:
    return item_match_value(retrieved_item) in set(gold_labels)


def build_relevance_vector(retrieved_results: list[dict[str, Any]], gold_labels: list[str], k: int | None = None) -> list[int]:
    items = retrieved_results[:k] if k is not None else retrieved_results
    return [1 if match_relevance(item, gold_labels) else 0 for item in items]


def unique_retrieved_gold_count(retrieved_results: list[dict[str, Any]], gold_labels: list[str], k: int) -> int:
    gold = set(gold_labels)
    retrieved = {item_match_value(item) for item in retrieved_results[:k]}
    return len(gold & retrieved)


def recall_at_k(retrieved_results: list[dict[str, Any]], gold_labels: list[str], k: int) -> float:
    if not gold_labels:
        return 0.0
    return unique_retrieved_gold_count(retrieved_results, gold_labels, k) / len(gold_labels)


def reciprocal_rank(relevance_vector: list[int]) -> tuple[int | None, float]:
    for index, relevant in enumerate(relevance_vector, 1):
        if relevant:
            return index, 1.0 / index
    return None, 0.0


def dcg(relevance_vector: list[int], k: int) -> float:
    return sum(float(rel) / math.log2(index + 1) for index, rel in enumerate(relevance_vector[:k], 1))


def idcg(gold_count: int, k: int) -> float:
    return sum(1.0 / math.log2(index + 1) for index in range(1, min(gold_count, k) + 1))


def ndcg_at_k(relevance_vector: list[int], gold_count: int, k: int) -> tuple[float, float, float]:
    actual = dcg(relevance_vector, k)
    ideal = idcg(gold_count, k)
    return actual, ideal, actual / ideal if ideal else 0.0


def compute_case_metric(row: dict[str, Any], k_for_mrr: int | None = None) -> CaseMetric:
    gold_labels = normalize_gold_labels(row.get("expected_knowledge_topics"))
    results = list(row.get("results") or [])
    top10 = results[:10]
    relevance_top10 = build_relevance_vector(top10, gold_labels)
    mrr_vector = build_relevance_vector(results[:k_for_mrr] if k_for_mrr else results, gold_labels)
    first_rank, rr = reciprocal_rank(mrr_vector)
    dcg5, idcg5, ndcg5 = ndcg_at_k(relevance_top10, len(gold_labels), 5)
    retrieved_top10 = [
        {
            "rank": index,
            "chunk_id": item.get("chunk_id"),
            "doc_id": item.get("doc_id"),
            "doc_type": item.get("doc_type"),
            "topic": item.get("doc_id"),
            "relevant": bool(relevance_top10[index - 1]),
        }
        for index, item in enumerate(top10, 1)
    ]
    return CaseMetric(
        case_id=str(row.get("case_id") or ""),
        gold_labels=gold_labels,
        retrieved_top10=retrieved_top10,
        relevance_vector_top10=relevance_top10,
        gold_count=len(gold_labels),
        retrieved_gold_count_at_5=unique_retrieved_gold_count(results, gold_labels, 5),
        retrieved_gold_count_at_10=unique_retrieved_gold_count(results, gold_labels, 10),
        hit_at_5=any(relevance_top10[:5]),
        recall_at_5=recall_at_k(results, gold_labels, 5),
        recall_at_10=recall_at_k(results, gold_labels, 10),
        first_relevant_rank=first_rank,
        reciprocal_rank=rr,
        dcg_at_5=dcg5,
        idcg_at_5=idcg5,
        ndcg_at_5=ndcg5,
        labeled=bool(gold_labels),
    )


def compute_retrieval_metrics(rows: list[dict[str, Any]]) -> dict[str, Any]:
    successful_rows = [row for row in rows if row.get("success")]
    if not successful_rows:
        return {
            "gold_source": "unavailable",
            "note": "retrieval requests did not succeed, so retrieval-quality metrics are unavailable for this run",
            "Recall@5": None,
            "Recall@10": None,
            "MRR": None,
            "nDCG@5": None,
        }
    case_metrics = [compute_case_metric(row) for row in successful_rows if row.get("expected_knowledge_topics")]
    if not case_metrics:
        return {
            "gold_source": "unavailable",
            "note": "gold retrieval labels are not available in current benchmark",
            "Recall@5": None,
            "Recall@10": None,
            "MRR": None,
            "nDCG@5": None,
        }
    denominator = len(case_metrics)
    return {
        "gold_source": f"existing EEMRS Eval V1 {GOLD_FIELD}",
        "gold_match_field": MATCH_FIELD,
        "labeled_cases": denominator,
        "Recall@5": sum(item.recall_at_5 for item in case_metrics) / denominator,
        "Recall@10": sum(item.recall_at_10 for item in case_metrics) / denominator,
        "MRR": sum(item.reciprocal_rank for item in case_metrics) / denominator,
        "nDCG@5": sum(item.ndcg_at_5 for item in case_metrics) / denominator,
    }
