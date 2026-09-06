"""Verify real Milvus V3 external-evidence retrieval and context expansion."""

from __future__ import annotations

import argparse
import json
import time
from pathlib import Path
from typing import Any

from rag.context.context_builder import ContextBuilder, ParentChildExpander
from rag.context.context_models import ContextPolicy
from rag.context.external_evidence_pipeline import EXTERNAL_DOC_TYPE, EXTERNAL_OUTPUT_FIELDS, ExternalEvidencePipeline
from rag.embedding_provider import EmbeddingProvider
from rag.eval.run_parent_child_context_v3 import QUERIES
from rag.milvus_client import MedicalRagMilvus
from rag.rag_config import MEDICAL_RAG_V3_COLLECTION_NAME, MILVUS_HOST, MILVUS_PORT, RAG_RRF_K
from rag.retrieval.dense_retriever import DenseRetriever
from rag.retrieval.reranker import get_default_reranker
from rag.retrieval.sparse_retriever import SparseRetriever
from rag.store.milvus_store import MilvusStore
from rag.store.parent_store import ParentStore


def write_jsonl(path: Path, rows: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as handle:
        for row in rows:
            handle.write(json.dumps(row, ensure_ascii=False) + "\n")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--artifact-path", required=True)
    parser.add_argument("--collection-name", default=MEDICAL_RAG_V3_COLLECTION_NAME)
    parser.add_argument("--with-reranker", action="store_true")
    args = parser.parse_args()

    artifact = Path(args.artifact_path)
    parent_store = ParentStore(artifact / "staging")
    provider = EmbeddingProvider()
    milvus = MedicalRagMilvus(MILVUS_HOST, MILVUS_PORT, args.collection_name)
    milvus.connect()
    store = MilvusStore(milvus)
    dense = DenseRetriever(store)
    sparse = SparseRetriever(store)
    dense.output_fields = EXTERNAL_OUTPUT_FIELDS
    sparse.output_fields = EXTERNAL_OUTPUT_FIELDS
    reranker = get_default_reranker() if args.with_reranker else None
    pipeline = ExternalEvidencePipeline(provider, store, rrf_k=RAG_RRF_K, reranker=reranker)
    rows = []
    for case in QUERIES:
        started = time.perf_counter()
        vector = provider.encode_texts([case["query"]])[0]
        dense_hits = dense.search(vector, top_k=5, doc_type=EXTERNAL_DOC_TYPE)
        sparse_hits = sparse.search(case["query"], top_k=5, doc_type=EXTERNAL_DOC_TYPE)
        children, retrieval_trace = pipeline.search(case["query"], expanded_query=case["query"], top_k=5)
        units, expansion_trace = ParentChildExpander(parent_store).expand(children, ContextPolicy(mode="child_with_neighbors"))
        package = ContextBuilder().build([], units, ContextPolicy(mode="child_with_neighbors"))
        expected = case["expected_source_id"]
        rows.append(
            {
                "case_id": case["case_id"],
                "query": case["query"],
                "expected_source_id": expected,
                "dense_top_child_ids": [item.chunk_id for item in dense_hits],
                "dense_source_hit": any(item.entity.get("source_id") == expected for item in dense_hits),
                "bm25_top_child_ids": [item.chunk_id for item in sparse_hits],
                "bm25_source_hit": any(item.entity.get("source_id") == expected for item in sparse_hits),
                "hybrid_selected_child_ids": [child.chunk_id for child in children],
                "hybrid_source_hit": any(child.source_id == expected for child in children),
                "reranker_enabled": bool(reranker),
                "included_parent_ids": package.included_parent_ids,
                "included_source_ids": package.included_source_ids,
                "final_context_hash": package.context_hash,
                "final_context_tokens": package.token_count,
                "evidence_chain_pass": expected in package.included_source_ids,
                "retrieval_trace": retrieval_trace,
                "parent_expansion_trace": expansion_trace,
                "context_builder_trace": package.trace,
                "latency_ms": int((time.perf_counter() - started) * 1000),
            }
        )
    write_jsonl(artifact / "retrieval" / "real_external_retrieval_check.jsonl", rows)
    summary = {
        "collection": args.collection_name,
        "case_count": len(rows),
        "dense_source_hit_rate": sum(1 for row in rows if row["dense_source_hit"]) / len(rows),
        "bm25_source_hit_rate": sum(1 for row in rows if row["bm25_source_hit"]) / len(rows),
        "hybrid_source_hit_rate": sum(1 for row in rows if row["hybrid_source_hit"]) / len(rows),
        "evidence_chain_pass_rate": sum(1 for row in rows if row["evidence_chain_pass"]) / len(rows),
        "reranker_enabled": bool(reranker),
    }
    (artifact / "retrieval" / "real_external_retrieval_summary.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0 if summary["hybrid_source_hit_rate"] > 0 else 2


if __name__ == "__main__":
    raise SystemExit(main())
