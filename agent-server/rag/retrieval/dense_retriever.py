"""Dense retrieval adapter for Medical RAG."""

from __future__ import annotations

from typing import Any

from rag.store.milvus_store import MilvusStore

from .fusion import RankedCandidate


class DenseRetriever:
    def __init__(self, store: MilvusStore):
        self.store = store
        self.output_fields: list[str] | None = None

    def search(
        self,
        query_vector: list[float],
        top_k: int,
        doc_type: str | None = None,
    ) -> list[RankedCandidate]:
        try:
            hits = self.store.search_dense(query_vector, top_k=top_k, doc_type=doc_type, output_fields=self.output_fields)
        except TypeError:
            hits = self.store.search_dense(query_vector, top_k=top_k, doc_type=doc_type)
        return ranked_dense_candidates(hits)


def ranked_dense_candidates(hits: list[dict[str, Any]]) -> list[RankedCandidate]:
    candidates: list[RankedCandidate] = []
    for rank, hit in enumerate(hits, 1):
        entity = hit.get("entity") or hit
        chunk_id = str(entity.get("chunk_id") or "")
        if not chunk_id:
            continue
        score = float(hit.get("distance", hit.get("score", 0.0)) or 0.0)
        candidates.append(RankedCandidate(chunk_id=chunk_id, rank=rank, score=score, entity=entity))
    return candidates
