"""Compare doctor-draft retrieval context across dense, hybrid RRF, and rerank modes."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

from rag.embedding_provider import EmbeddingProvider
from rag.milvus_client import MedicalRagMilvus
from rag.rag_config import MILVUS_COLLECTION_NAME, MILVUS_HOST, MILVUS_PORT, RAG_HYBRID_COLLECTION_NAME, RAG_RRF_K
from rag.retrieval.retrieval_service import RetrievalService
from rag.schema.retrieval_models import RetrieveRequest
from rag.store.milvus_store import MilvusStore


PROJECT_ROOT = Path(__file__).resolve().parents[2]
DATASET = PROJECT_ROOT / "evaluation" / "EEMRS_Eval_V1" / "datasets" / "doctor_draft_dev.jsonl"
AGENT_RESULTS = PROJECT_ROOT / "evaluation" / "EEMRS_Eval_V1" / "runner" / "results" / "baseline_v1"


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def connect_store(collection_name: str) -> MilvusStore:
    client = MedicalRagMilvus(MILVUS_HOST, MILVUS_PORT, collection_name)
    client.connect()
    return MilvusStore(client)


def query_for_case(case: dict[str, Any]) -> str:
    source = case.get("input") or {}
    conclusion = source.get("consultation_summary") or ""
    history = source.get("consultation_trace") or []
    history_text = "\n".join(f"{item.get('role', '')}: {item.get('content', '')}" for item in history)
    return (conclusion + "\n" + history_text).strip()


def load_agent_rows(run_id: str) -> dict[str, dict[str, Any]]:
    path = AGENT_RESULTS / run_id / "case_results.jsonl"
    if not path.exists():
        return {}
    return {row["case_id"]: row for row in read_jsonl(path)}


def retrieve_rows(provider: EmbeddingProvider, stores: dict[str, MilvusStore], case: dict[str, Any], top_k: int) -> dict[str, Any]:
    rows = {}
    query = query_for_case(case)
    for mode in ["dense", "hybrid_rrf", "hybrid_rerank"]:
        service = RetrievalService(provider, stores[mode], retrieval_mode=mode, rrf_k=RAG_RRF_K)
        response = service.retrieve(RetrieveRequest(query=query, scene="medical_record", top_k=top_k), {"case_id": case["metadata"]["case_id"]})
        rows[mode] = {
            "success": response.success,
            "error_message": response.error_message,
            "query": query,
            "chunks": [
                {
                    "rank": index,
                    "chunk_id": chunk.chunk_id,
                    "doc_id": chunk.doc_id,
                    "doc_type": chunk.doc_type,
                    "score": chunk.score,
                    "final_score": chunk.final_score,
                    "title": chunk.title,
                }
                for index, chunk in enumerate(response.chunks, 1)
            ],
            "trace": response.trace_meta.get("hybrid_retrieval") if response.trace_meta else None,
        }
    return rows


def compare_chunks(left: list[dict[str, Any]], right: list[dict[str, Any]]) -> dict[str, Any]:
    left_ids = [item.get("chunk_id") for item in left]
    right_ids = [item.get("chunk_id") for item in right]
    left_rank = {chunk_id: index for index, chunk_id in enumerate(left_ids, 1)}
    right_rank = {chunk_id: index for index, chunk_id in enumerate(right_ids, 1)}
    return {
        "added": [chunk_id for chunk_id in right_ids if chunk_id not in left_ids],
        "removed": [chunk_id for chunk_id in left_ids if chunk_id not in right_ids],
        "rank_changes": [
            {"chunk_id": chunk_id, "left_rank": left_rank.get(chunk_id), "right_rank": right_rank.get(chunk_id)}
            for chunk_id in sorted(set(left_ids) | set(right_ids))
            if left_rank.get(chunk_id) != right_rank.get(chunk_id)
        ],
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True)
    parser.add_argument("--dense-run-id", required=True)
    parser.add_argument("--hybrid-rrf-run-id", required=True)
    parser.add_argument("--hybrid-rerank-run-id", required=True)
    parser.add_argument("--top-k", type=int, default=8)
    args = parser.parse_args()

    provider = EmbeddingProvider()
    stores = {
        "dense": connect_store(MILVUS_COLLECTION_NAME),
        "hybrid_rrf": connect_store(RAG_HYBRID_COLLECTION_NAME),
        "hybrid_rerank": connect_store(RAG_HYBRID_COLLECTION_NAME),
    }
    agent_rows = {
        "dense": load_agent_rows(args.dense_run_id),
        "hybrid_rrf": load_agent_rows(args.hybrid_rrf_run_id),
        "hybrid_rerank": load_agent_rows(args.hybrid_rerank_run_id),
    }
    cases = read_jsonl(DATASET)[:5]
    output_cases = []
    for case in cases:
        case_id = case["metadata"]["case_id"]
        retrieval = retrieve_rows(provider, stores, case, args.top_k)
        output_cases.append({
            "case_id": case_id,
            "agent_status": {
                mode: {
                    "execution_status": agent_rows[mode].get(case_id, {}).get("execution_status"),
                    "case_status": agent_rows[mode].get(case_id, {}).get("evaluation", {}).get("case_status")
                    or agent_rows[mode].get(case_id, {}).get("evaluation", {}).get("task_gate"),
                    "root_cause": agent_rows[mode].get(case_id, {}).get("root_cause"),
                    "error": agent_rows[mode].get(case_id, {}).get("error"),
                    "agent_retrieval_trace_observable": bool(agent_rows[mode].get(case_id, {}).get("retrieval_trace")),
                }
                for mode in ["dense", "hybrid_rrf", "hybrid_rerank"]
            },
            "direct_retrieval_replay": retrieval,
            "dense_vs_hybrid_rrf": compare_chunks(retrieval["dense"]["chunks"], retrieval["hybrid_rrf"]["chunks"]),
            "hybrid_rrf_vs_hybrid_rerank": compare_chunks(retrieval["hybrid_rrf"]["chunks"], retrieval["hybrid_rerank"]["chunks"]),
            "attribution_note": "Agent trace payload did not expose doctor_draft RAG context in these runs; retrieval context comparison is a direct replay using the same medical_record scene and doctor_draft dataset input.",
        })
    result = {
        "dense_run_id": args.dense_run_id,
        "hybrid_rrf_run_id": args.hybrid_rrf_run_id,
        "hybrid_rerank_run_id": args.hybrid_rerank_run_id,
        "collection_v1": MILVUS_COLLECTION_NAME,
        "collection_v2": RAG_HYBRID_COLLECTION_NAME,
        "cases": output_cases,
    }
    Path(args.output).write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(args.output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
