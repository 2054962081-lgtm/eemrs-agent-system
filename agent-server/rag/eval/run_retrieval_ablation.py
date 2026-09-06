"""Run dense, BM25, and hybrid retrieval ablation against the same DEV queries."""

from __future__ import annotations

import argparse
import json
import math
import time
from collections import Counter
from datetime import datetime
from pathlib import Path
from typing import Any

from rag.embedding_provider import EmbeddingProvider
from rag.eval.run_baseline import load_retrieval_cases, retrieval_metrics
from rag.milvus_client import MedicalRagMilvus
from rag.rag_config import (
    MILVUS_COLLECTION_NAME,
    MILVUS_HOST,
    MILVUS_PORT,
    RAG_HYBRID_COLLECTION_NAME,
    RAG_RRF_K,
)
from rag.schema.retrieval_models import RetrieveRequest
from rag.store.milvus_store import MilvusStore
from rag.retrieval.retrieval_service import RetrievalService


PROJECT_ROOT = Path(__file__).resolve().parents[2]
ARTIFACT_ROOT = PROJECT_ROOT / "rag" / "eval" / "artifacts"


def percentile(values: list[int], p: float) -> float | None:
    if not values:
        return None
    ordered = sorted(values)
    if len(ordered) == 1:
        return float(ordered[0])
    rank = (len(ordered) - 1) * p
    lower = math.floor(rank)
    upper = math.ceil(rank)
    if lower == upper:
        return float(ordered[lower])
    return float(ordered[lower] + (ordered[upper] - ordered[lower]) * (rank - lower))


def connect_store(collection_name: str) -> MilvusStore:
    client = MedicalRagMilvus(MILVUS_HOST, MILVUS_PORT, collection_name)
    client.connect()
    if not client.has_collection():
        raise RuntimeError(f"collection does not exist: {collection_name}")
    return MilvusStore(client)


def row_from_response(case: dict[str, Any], response: Any, mode: str, latency_ms: int) -> dict[str, Any]:
    chunks = [chunk.model_dump() for chunk in response.chunks]
    trace = response.trace_meta or {}
    return {
        "case_id": case["case_id"],
        "query_original": case["query"],
        "query_expanded": response.expanded_query,
        "scene": "pre_inquiry",
        "top_k": len(chunks),
        "mode": mode,
        "success": response.success,
        "results": [
            {
                "rank": index,
                "chunk_id": chunk.get("chunk_id"),
                "doc_id": chunk.get("doc_id"),
                "doc_type": chunk.get("doc_type"),
                "score": chunk.get("score"),
                "final_score": chunk.get("final_score"),
                "title": chunk.get("title"),
            }
            for index, chunk in enumerate(chunks, 1)
        ],
        "latency_ms": latency_ms,
        "retrieval_selection": trace.get("retrieval_selection"),
        "hybrid_retrieval": trace.get("hybrid_retrieval"),
        "bm25_retrieval": trace.get("bm25_retrieval"),
        "rag_ground_truth_present": bool(case.get("rag_ground_truth")),
        "expected_knowledge_topics": case.get("rag_ground_truth", {}).get("expected_knowledge_topics", []),
        "error_message": response.error_message,
    }


def summarize(rows: list[dict[str, Any]]) -> dict[str, Any]:
    latencies = [row["latency_ms"] for row in rows if row.get("success")]
    returned_counts = [len(row.get("results", [])) for row in rows]
    doc_types = Counter()
    for row in rows:
        for result in row.get("results", []):
            doc_types[result.get("doc_type") or "unknown"] += 1
    total = len(rows)
    successes = sum(1 for row in rows if row.get("success"))
    return {
        "total_cases": total,
        "successful_cases": successes,
        "failed_cases": total - successes,
        "request_success_rate": successes / total if total else 0.0,
        "average_latency_ms": sum(latencies) / len(latencies) if latencies else None,
        "p50_latency_ms": percentile(latencies, 0.50),
        "p95_latency_ms": percentile(latencies, 0.95),
        "average_returned_chunks": sum(returned_counts) / len(returned_counts) if returned_counts else 0.0,
        "doc_type_distribution": dict(sorted(doc_types.items())),
        "retrieval_metrics": retrieval_metrics(rows),
    }


def compare_modes(dense_rows: list[dict[str, Any]], bm25_rows: list[dict[str, Any]], hybrid_rows: list[dict[str, Any]]) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    bm25_by_id = {row["case_id"]: row for row in bm25_rows}
    hybrid_by_id = {row["case_id"]: row for row in hybrid_rows}
    diff_cases = []
    overlap_values = []
    sparse_rescue_count = 0
    for dense in dense_rows:
        cid = dense["case_id"]
        bm25 = bm25_by_id.get(cid, {})
        hybrid = hybrid_by_id.get(cid, {})
        dense_ids = [item.get("chunk_id") for item in dense.get("results", [])]
        bm25_ids = [item.get("chunk_id") for item in bm25.get("results", [])]
        hybrid_ids = [item.get("chunk_id") for item in hybrid.get("results", [])]
        overlap = len(set(dense_ids) & set(bm25_ids))
        overlap_values.append(overlap)
        added_by_bm25 = [chunk_id for chunk_id in hybrid_ids if chunk_id not in dense_ids and chunk_id in bm25_ids]
        sparse_rescue_count += len(added_by_bm25)
        if dense_ids[:5] != hybrid_ids[:5]:
            dense_rank = {chunk_id: index for index, chunk_id in enumerate(dense_ids, 1)}
            hybrid_rank = {chunk_id: index for index, chunk_id in enumerate(hybrid_ids, 1)}
            diff_cases.append({
                "case_id": cid,
                "dense_top5": dense.get("results", [])[:5],
                "hybrid_top5": hybrid.get("results", [])[:5],
                "added_by_bm25": added_by_bm25,
                "removed_after_rrf": [chunk_id for chunk_id in dense_ids[:5] if chunk_id not in hybrid_ids[:5]],
                "rank_changes": [
                    {"chunk_id": chunk_id, "dense_rank": dense_rank.get(chunk_id), "hybrid_rank": hybrid_rank.get(chunk_id)}
                    for chunk_id in sorted(set(dense_ids) | set(hybrid_ids))
                    if dense_rank.get(chunk_id) != hybrid_rank.get(chunk_id)
                ],
            })
    comparison = {
        "dense": summarize(dense_rows),
        "bm25": summarize(bm25_rows),
        "hybrid_rrf": summarize(hybrid_rows),
        "dense_sparse_average_overlap": sum(overlap_values) / len(overlap_values) if overlap_values else None,
        "sparse_rescue_count": sparse_rescue_count,
        "hybrid_diff_case_count": len(diff_cases),
    }
    return comparison, diff_cases


def write_jsonl(path: Path, rows: list[dict[str, Any]]) -> None:
    path.write_text("".join(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n" for row in rows), encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dataset", default="consultation", choices=["consultation"])
    parser.add_argument("--split", default="dev", choices=["dev"])
    parser.add_argument("--limit", type=int, default=20)
    parser.add_argument("--scene", default="pre_inquiry")
    parser.add_argument("--top-k", type=int, default=8)
    args = parser.parse_args()

    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    output_dir = ARTIFACT_ROOT / f"retrieval_ablation_{timestamp}"
    output_dir.mkdir(parents=True, exist_ok=False)

    provider = EmbeddingProvider()
    stores = {
        "dense": connect_store(MILVUS_COLLECTION_NAME),
        "bm25": connect_store(RAG_HYBRID_COLLECTION_NAME),
        "hybrid_rrf": connect_store(RAG_HYBRID_COLLECTION_NAME),
    }
    cases = load_retrieval_cases(args.dataset, args.split, args.limit)
    rows_by_mode: dict[str, list[dict[str, Any]]] = {"dense": [], "bm25": [], "hybrid_rrf": []}
    for mode, store in stores.items():
        service = RetrievalService(provider, store, retrieval_mode=mode, rrf_k=RAG_RRF_K)
        for case in cases:
            request = RetrieveRequest(query=case["query"], top_k=args.top_k, scene=args.scene)
            started = time.perf_counter()
            response = service.retrieve(request, {"run_id": f"retrieval_ablation_{timestamp}", "case_id": case["case_id"]})
            latency_ms = int((time.perf_counter() - started) * 1000)
            rows_by_mode[mode].append(row_from_response(case, response, mode, latency_ms))

    comparison, diff_cases = compare_modes(rows_by_mode["dense"], rows_by_mode["bm25"], rows_by_mode["hybrid_rrf"])
    manifest = {
        "run_id": f"retrieval_ablation_{timestamp}",
        "collection_v1": MILVUS_COLLECTION_NAME,
        "collection_v2": RAG_HYBRID_COLLECTION_NAME,
        "rrf_k": RAG_RRF_K,
        "dataset": args.dataset,
        "split": args.split,
        "limit": args.limit,
        "scene": args.scene,
        "top_k": args.top_k,
        "gold_label_policy": "use existing rag_ground_truth.expected_knowledge_topics only; no new labels created",
    }
    (output_dir / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    write_jsonl(output_dir / "dense_results.jsonl", rows_by_mode["dense"])
    write_jsonl(output_dir / "bm25_results.jsonl", rows_by_mode["bm25"])
    write_jsonl(output_dir / "hybrid_results.jsonl", rows_by_mode["hybrid_rrf"])
    write_jsonl(output_dir / "hybrid_diff_cases.jsonl", diff_cases)
    (output_dir / "comparison.json").write_text(json.dumps(comparison, ensure_ascii=False, indent=2), encoding="utf-8")
    (output_dir / "README.md").write_text(
        "# Medical RAG V2 Retrieval Ablation\n\n"
        "This artifact compares dense, BM25, and dense+BM25 RRF retrieval on the same DEV queries. "
        "BM25 uses the V2 Milvus collection; dense uses the frozen V1 collection.\n",
        encoding="utf-8",
    )
    print(json.dumps({"artifact_path": str(output_dir), "comparison": comparison}, ensure_ascii=False, indent=2))
    failed = sum(summary["failed_cases"] for summary in (comparison["dense"], comparison["bm25"], comparison["hybrid_rrf"]))
    return 0 if failed == 0 else 2


if __name__ == "__main__":
    raise SystemExit(main())
