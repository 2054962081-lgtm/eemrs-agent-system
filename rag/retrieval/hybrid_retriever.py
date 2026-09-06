"""Hybrid dense + BM25 retrieval with doc_type-local RRF fusion."""

from __future__ import annotations

import time
from dataclasses import dataclass
from typing import Any

from rag.store.milvus_store import MilvusStore

from .dense_retriever import DenseRetriever
from .fusion import FusedCandidate, fused_candidate_trace, fuse_rrf
from .sparse_retriever import SparseRetriever


@dataclass(frozen=True)
class HybridDocTypeResult:
    doc_type: str
    fused_candidates: list[FusedCandidate]
    trace: dict[str, Any]


class HybridRetriever:
    def __init__(self, store: MilvusStore, rrf_k: int = 60, output_fields: list[str] | None = None):
        self.dense = DenseRetriever(store)
        self.sparse = SparseRetriever(store)
        self.dense.output_fields = output_fields
        self.sparse.output_fields = output_fields
        self.rrf_k = rrf_k

    def search_doc_type(
        self,
        dense_query_vector: list[float],
        sparse_query: str,
        top_k: int,
        doc_type: str,
    ) -> HybridDocTypeResult:
        started = time.perf_counter()
        dense_started = time.perf_counter()
        dense_candidates = self.dense.search(dense_query_vector, top_k=top_k, doc_type=doc_type)
        dense_ms = int((time.perf_counter() - dense_started) * 1000)

        sparse_started = time.perf_counter()
        sparse_candidates = self.sparse.search(sparse_query, top_k=top_k, doc_type=doc_type)
        sparse_ms = int((time.perf_counter() - sparse_started) * 1000)

        fusion_started = time.perf_counter()
        fused = fuse_rrf(dense_candidates, sparse_candidates, self.rrf_k)
        fusion_ms = int((time.perf_counter() - fusion_started) * 1000)

        trace = {
            "doc_type": doc_type,
            "dense_candidates": [
                {
                    "rank": item.rank,
                    "chunk_id": item.chunk_id,
                    "doc_id": item.entity.get("doc_id"),
                    "doc_type": item.entity.get("doc_type"),
                    "title": item.entity.get("title"),
                    "score": item.score,
                }
                for item in dense_candidates
            ],
            "sparse_candidates": [
                {
                    "rank": item.rank,
                    "chunk_id": item.chunk_id,
                    "doc_id": item.entity.get("doc_id"),
                    "doc_type": item.entity.get("doc_type"),
                    "title": item.entity.get("title"),
                    "score": item.score,
                }
                for item in sparse_candidates
            ],
            "fused_candidates": [
                fused_candidate_trace(item, rank)
                for rank, item in enumerate(fused, 1)
            ],
            "latency": {
                "dense_ms": dense_ms,
                "sparse_ms": sparse_ms,
                "fusion_ms": fusion_ms,
                "total_ms": int((time.perf_counter() - started) * 1000),
            },
        }
        return HybridDocTypeResult(doc_type=doc_type, fused_candidates=fused, trace=trace)
