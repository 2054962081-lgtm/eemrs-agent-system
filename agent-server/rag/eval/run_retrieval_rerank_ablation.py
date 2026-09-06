"""Run dense, BM25, hybrid RRF, and hybrid rerank retrieval ablation."""

from __future__ import annotations

import argparse
import importlib.metadata
import json
import math
import time
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from rag.embedding_provider import EmbeddingProvider
from rag.eval.run_baseline import load_retrieval_cases, retrieval_metrics
from rag.milvus_client import MedicalRagMilvus
from rag.rag_config import (
    EMBEDDING_MODEL_NAME,
    MILVUS_COLLECTION_NAME,
    MILVUS_HOST,
    MILVUS_PORT,
    RAG_BM25_ANALYZER,
    RAG_HYBRID_COLLECTION_NAME,
    RAG_RERANKER_BATCH_SIZE,
    RAG_RERANKER_DEVICE,
    RAG_RERANKER_LOCAL_ONLY,
    RAG_RERANKER_MAX_LENGTH,
    RAG_RERANKER_MODEL_NAME,
    RAG_RERANKER_MODEL_REVISION,
    RAG_RRF_K,
)
from rag.retrieval.reranker import default_reranker_info
from rag.retrieval.retrieval_service import RetrievalService
from rag.schema.retrieval_models import RetrieveRequest
from rag.store.milvus_store import MilvusStore


PROJECT_ROOT = Path(__file__).resolve().parents[2]
ARTIFACT_ROOT = PROJECT_ROOT / "rag" / "eval" / "artifacts"
EVAL_ROOT = PROJECT_ROOT / "rag" / "eval"


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


def row_from_response(case: dict[str, Any], response: Any, mode: str, latency_ms: int, scene: str, top_k: int) -> dict[str, Any]:
    chunks = [chunk.model_dump() for chunk in response.chunks]
    trace = response.trace_meta or {}
    return {
        "case_id": case["case_id"],
        "query_original": case["query"],
        "query_expanded": response.expanded_query,
        "scene": scene,
        "top_k": top_k,
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


def trace_candidates(row: dict[str, Any], key: str) -> list[dict[str, Any]]:
    traces = ((row.get("hybrid_retrieval") or {}).get("doc_type_traces") or [])
    candidates: list[dict[str, Any]] = []
    for trace in traces:
        candidates.extend(trace.get(key) or [])
    return candidates


def ranking_correction_metrics(rrf_rows: list[dict[str, Any]], rerank_rows: list[dict[str, Any]]) -> dict[str, Any]:
    rrf_by_id = {row["case_id"]: row for row in rrf_rows}
    promoted = demoted = top1_correction = top1_regression = 0
    labeled_cases = 0
    for rerank in rerank_rows:
        expected = set(rerank.get("expected_knowledge_topics") or [])
        if not expected:
            continue
        labeled_cases += 1
        rrf = rrf_by_id.get(rerank["case_id"], {})
        rrf_candidates = trace_candidates(rrf, "fused_candidates")
        rerank_candidates = trace_candidates(rerank, "rerank_candidates")
        rrf_rank_by_doc = best_rank_by_doc_id(rrf_candidates, "rank")
        rerank_rank_by_doc = best_rank_by_doc_id(rerank_candidates, "rerank_rank")
        for doc_id in expected:
            if doc_id not in rrf_rank_by_doc or doc_id not in rerank_rank_by_doc:
                continue
            if rerank_rank_by_doc[doc_id] < rrf_rank_by_doc[doc_id]:
                promoted += 1
            elif rerank_rank_by_doc[doc_id] > rrf_rank_by_doc[doc_id]:
                demoted += 1
        rrf_top1 = (rrf.get("results") or [{}])[0].get("doc_id")
        rerank_top1 = (rerank.get("results") or [{}])[0].get("doc_id")
        candidate_gold_present = bool(expected & set(rerank_rank_by_doc))
        if candidate_gold_present and rrf_top1 not in expected and rerank_top1 in expected:
            top1_correction += 1
        if candidate_gold_present and rrf_top1 in expected and rerank_top1 not in expected:
            top1_regression += 1
    return {
        "gold_source": "existing EEMRS Eval V1 rag_ground_truth.expected_knowledge_topics" if labeled_cases else "unavailable",
        "labeled_cases": labeled_cases,
        "rerank_promoted_gold_count": promoted if labeled_cases else None,
        "rerank_demoted_gold_count": demoted if labeled_cases else None,
        "rerank_top1_correction_count": top1_correction if labeled_cases else None,
        "rerank_top1_regression_count": top1_regression if labeled_cases else None,
    }


def best_rank_by_doc_id(candidates: list[dict[str, Any]], rank_key: str) -> dict[str, int]:
    ranks: dict[str, int] = {}
    for candidate in candidates:
        doc_id = candidate.get("doc_id")
        rank = candidate.get(rank_key) or candidate.get("rank")
        if not doc_id or rank is None:
            continue
        ranks[doc_id] = min(ranks.get(doc_id, 10**9), int(rank))
    return ranks


def diff_cases(dense_rows: list[dict[str, Any]], rrf_rows: list[dict[str, Any]], rerank_rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
    dense_by_id = {row["case_id"]: row for row in dense_rows}
    rrf_by_id = {row["case_id"]: row for row in rrf_rows}
    diffs = []
    for rerank in rerank_rows:
        cid = rerank["case_id"]
        dense = dense_by_id.get(cid, {})
        rrf = rrf_by_id.get(cid, {})
        dense_ids = [item.get("chunk_id") for item in dense.get("results", [])]
        rrf_ids = [item.get("chunk_id") for item in rrf.get("results", [])]
        rerank_ids = [item.get("chunk_id") for item in rerank.get("results", [])]
        if rrf_ids == rerank_ids and dense_ids == rerank_ids:
            continue
        expected = set(rerank.get("expected_knowledge_topics") or [])
        rrf_top1 = (rrf.get("results") or [{}])[0].get("doc_id")
        rerank_top1 = (rerank.get("results") or [{}])[0].get("doc_id")
        if rrf_top1 not in expected and rerank_top1 in expected:
            category = "RRF_ERROR_RERANKER_CORRECTED"
        elif rrf_top1 in expected and rerank_top1 not in expected:
            category = "RRF_CORRECT_RERANKER_REGRESSED"
        elif (dense.get("results") or [{}])[0].get("doc_id") in expected and rrf_top1 not in expected and rerank_top1 in expected:
            category = "DENSE_CORRECT_HYBRID_REGRESSED_RERANKER_RECOVERED"
        else:
            category = "RERANKER_UNRESOLVED_OR_NEUTRAL"
        diffs.append({
            "case_id": cid,
            "category": category,
            "expected_knowledge_topics": sorted(expected),
            "dense_top5": dense.get("results", [])[:5],
            "hybrid_rrf_top5": rrf.get("results", [])[:5],
            "hybrid_rerank_top5": rerank.get("results", [])[:5],
            "rank_changes": rank_changes(rrf_ids, rerank_ids),
        })
    return diffs


def rank_changes(left_ids: list[str], right_ids: list[str]) -> list[dict[str, Any]]:
    left_rank = {chunk_id: index for index, chunk_id in enumerate(left_ids, 1)}
    right_rank = {chunk_id: index for index, chunk_id in enumerate(right_ids, 1)}
    return [
        {"chunk_id": chunk_id, "hybrid_rrf_rank": left_rank.get(chunk_id), "hybrid_rerank_rank": right_rank.get(chunk_id)}
        for chunk_id in sorted(set(left_ids) | set(right_ids))
        if left_rank.get(chunk_id) != right_rank.get(chunk_id)
    ]


def write_jsonl(path: Path, rows: list[dict[str, Any]]) -> None:
    path.write_text("".join(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n" for row in rows), encoding="utf-8")


def manifest(output_dir: Path, args: argparse.Namespace) -> dict[str, Any]:
    info = default_reranker_info()
    data = {
        "rag_version": "medical_rag_v2_hybrid_rerank",
        "created_at": datetime.now(timezone.utc).isoformat(),
        "collection_v1": MILVUS_COLLECTION_NAME,
        "collection_v2": RAG_HYBRID_COLLECTION_NAME,
        "dense": {"embedding_model": EMBEDDING_MODEL_NAME, "query": "expanded_query"},
        "sparse": {"engine": "milvus_bm25", "analyzer": RAG_BM25_ANALYZER, "query": "original_query"},
        "fusion": {"method": "RRF", "rrf_k": RAG_RRF_K},
        "reranker": {
            "enabled": True,
            "model": info["model"],
            "revision": info["revision"],
            "device": info["device"],
            "query": "original_query",
            "document": "chunk_text",
            "candidate_pool_size": "current fused candidates per doc_type bucket",
            "max_length": RAG_RERANKER_MAX_LENGTH,
            "batch_size": RAG_RERANKER_BATCH_SIZE,
            "local_files_only": RAG_RERANKER_LOCAL_ONLY,
            "loaded": info["loaded"],
        },
        "parent_child": False,
        "medical_rescoring": False,
        "query_rewrite": False,
        "dataset": args.dataset,
        "split": args.split,
        "limit": args.limit,
        "scene": args.scene,
        "top_k": args.top_k,
        "artifact_path": str(output_dir),
        "dependency_versions": {
            "torch": safe_version("torch"),
            "transformers": safe_version("transformers"),
            "sentence-transformers": safe_version("sentence-transformers"),
        },
    }
    (EVAL_ROOT / "medical_rag_v2_hybrid_rerank_manifest.json").write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    return data


def safe_version(name: str) -> str | None:
    try:
        return importlib.metadata.version(name)
    except importlib.metadata.PackageNotFoundError:
        return None


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dataset", default="consultation", choices=["consultation"])
    parser.add_argument("--split", default="dev", choices=["dev"])
    parser.add_argument("--limit", type=int, default=20)
    parser.add_argument("--scene", default="pre_inquiry")
    parser.add_argument("--top-k", type=int, default=8)
    args = parser.parse_args()

    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    output_dir = ARTIFACT_ROOT / f"retrieval_ablation_rerank_{timestamp}"
    output_dir.mkdir(parents=True, exist_ok=False)

    provider = EmbeddingProvider()
    stores = {
        "dense": connect_store(MILVUS_COLLECTION_NAME),
        "bm25": connect_store(RAG_HYBRID_COLLECTION_NAME),
        "hybrid_rrf": connect_store(RAG_HYBRID_COLLECTION_NAME),
        "hybrid_rerank": connect_store(RAG_HYBRID_COLLECTION_NAME),
    }
    cases = load_retrieval_cases(args.dataset, args.split, args.limit)
    rows_by_mode: dict[str, list[dict[str, Any]]] = {mode: [] for mode in stores}
    for mode, store in stores.items():
        service = RetrievalService(provider, store, retrieval_mode=mode, rrf_k=RAG_RRF_K)
        for case in cases:
            request = RetrieveRequest(query=case["query"], top_k=args.top_k, scene=args.scene)
            started = time.perf_counter()
            response = service.retrieve(request, {"run_id": f"retrieval_ablation_rerank_{timestamp}", "case_id": case["case_id"]})
            latency_ms = int((time.perf_counter() - started) * 1000)
            rows_by_mode[mode].append(row_from_response(case, response, mode, latency_ms, args.scene, args.top_k))

    comparison = {mode: summarize(rows) for mode, rows in rows_by_mode.items()}
    comparison["ranking_correction"] = ranking_correction_metrics(rows_by_mode["hybrid_rrf"], rows_by_mode["hybrid_rerank"])
    diffs = diff_cases(rows_by_mode["dense"], rows_by_mode["hybrid_rrf"], rows_by_mode["hybrid_rerank"])
    run_manifest = manifest(output_dir, args)
    (output_dir / "manifest.json").write_text(json.dumps(run_manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    write_jsonl(output_dir / "dense_results.jsonl", rows_by_mode["dense"])
    write_jsonl(output_dir / "bm25_results.jsonl", rows_by_mode["bm25"])
    write_jsonl(output_dir / "hybrid_rrf_results.jsonl", rows_by_mode["hybrid_rrf"])
    write_jsonl(output_dir / "hybrid_rerank_results.jsonl", rows_by_mode["hybrid_rerank"])
    write_jsonl(output_dir / "rerank_diff_cases.jsonl", diffs)
    (output_dir / "comparison.json").write_text(json.dumps(comparison, ensure_ascii=False, indent=2), encoding="utf-8")
    (output_dir / "README.md").write_text(
        "# Medical RAG V2 Hybrid Rerank Ablation\n\n"
        "Compares dense, BM25, hybrid RRF, and hybrid RRF plus cross-encoder reranking on the same DEV queries.\n",
        encoding="utf-8",
    )
    print(json.dumps({"artifact_path": str(output_dir), "comparison": comparison}, ensure_ascii=False, indent=2))
    failed = sum(comparison[mode]["failed_cases"] for mode in stores)
    return 0 if failed == 0 else 2


if __name__ == "__main__":
    raise SystemExit(main())
