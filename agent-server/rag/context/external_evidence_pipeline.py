"""External evidence adapter that reuses the existing V2.2 retrieval classes."""

from __future__ import annotations

import time
from typing import Any

from rag.embedding_provider import EmbeddingProvider
from rag.rag_config import RAG_RRF_K
from rag.retrieval.dense_retriever import DenseRetriever
from rag.retrieval.hybrid_retriever import HybridRetriever
from rag.retrieval.reranker import CrossEncoderReranker, get_default_reranker
from rag.retrieval.sparse_retriever import SparseRetriever
from rag.store.milvus_store import MilvusStore
from rag.store.parent_store import StoredChild


EXTERNAL_DOC_TYPE = "external_evidence"
EXTERNAL_OUTPUT_FIELDS = [
    "chunk_id",
    "parent_id",
    "source_id",
    "source_title",
    "publisher",
    "publication_date",
    "version",
    "source_url",
    "document_type",
    "source_status",
    "license_status",
    "knowledge_origin",
    "heading_path",
    "page_start",
    "page_end",
    "doc_type",
    "chunk_text",
    "embedding_text",
    "token_count",
    "embedding_token_count",
]


class ExternalEvidencePipeline:
    """Milvus V3 child search using DenseRetriever/SparseRetriever/RRF/Reranker."""

    def __init__(
        self,
        provider: EmbeddingProvider,
        store: MilvusStore,
        rrf_k: int = RAG_RRF_K,
        reranker: CrossEncoderReranker | None = None,
    ):
        self.provider = provider
        self.store = store
        self.rrf_k = rrf_k
        self.reranker = reranker

    def search(self, query: str, expanded_query: str | None = None, top_k: int = 5) -> tuple[list[StoredChild], dict[str, Any]]:
        started = time.perf_counter()
        dense_query = expanded_query or query
        vector = self.provider.encode_texts([dense_query])[0]
        hybrid = HybridRetriever(self.store, rrf_k=self.rrf_k, output_fields=EXTERNAL_OUTPUT_FIELDS)
        result = hybrid.search_doc_type(vector, query, top_k=top_k, doc_type=EXTERNAL_DOC_TYPE)
        candidates = result.fused_candidates
        if self.reranker:
            rerank_started = time.perf_counter()
            candidates = self.reranker.rerank(query, candidates)
            result.trace["latency"]["rerank_ms"] = int((time.perf_counter() - rerank_started) * 1000)
        selected = candidates[:top_k]
        children = [
            StoredChild.from_dict(_external_entity_to_child_row(candidate.entity)).with_rank(
                rank,
                float(candidate.entity.get("_rerank_score") or candidate.rrf_score or candidate.dense_score or 0.0),
            )
            for rank, candidate in enumerate(selected, 1)
        ]
        trace = {
            "collection": self.store.collection_name,
            "retrieval_mode": "hybrid_rerank" if self.reranker else "hybrid_rrf",
            "rrf_k": self.rrf_k,
            "dense_retriever_class": f"{DenseRetriever.__module__}.{DenseRetriever.__name__}",
            "sparse_retriever_class": f"{SparseRetriever.__module__}.{SparseRetriever.__name__}",
            "hybrid_retriever_class": f"{HybridRetriever.__module__}.{HybridRetriever.__name__}",
            "reranker_class": f"{self.reranker.__class__.__module__}.{self.reranker.__class__.__name__}" if self.reranker else None,
            "selected": [
                {
                    "retrieval_rank": rank,
                    "chunk_id": child.chunk_id,
                    "parent_id": child.parent_id,
                    "source_id": child.source_id,
                    "score": child.score,
                }
                for rank, child in enumerate(children, 1)
            ],
            "hybrid_trace": result.trace,
            "latency": {"total_ms": int((time.perf_counter() - started) * 1000)},
        }
        return children, trace


def _external_entity_to_child_row(entity: dict[str, Any]) -> dict[str, Any]:
    row = dict(entity)
    row.setdefault("knowledge_origin", "external_evidence")
    row.setdefault("document_type", row.get("document_type") or row.get("doc_type") or "external_evidence")
    row.setdefault("source_title", row.get("source_title") or row.get("title") or "")
    row.setdefault("publisher", row.get("publisher") or "")
    row.setdefault("source_url", row.get("source_url") or "")
    row.setdefault("heading_path", row.get("heading_path") or [])
    row.setdefault("embedding_text", row.get("embedding_text") or row.get("chunk_text") or "")
    row.setdefault("token_count", row.get("token_count") or 0)
    row.setdefault("embedding_token_count", row.get("embedding_token_count") or row.get("token_count") or 0)
    return row


def default_external_pipeline(provider: EmbeddingProvider, store: MilvusStore, enable_reranker: bool = True) -> ExternalEvidencePipeline:
    return ExternalEvidencePipeline(provider, store, reranker=get_default_reranker() if enable_reranker else None)
