"""Replay existing DEV evaluation queries against /rag/retrieve and save baseline artifacts."""

from __future__ import annotations

import argparse
import json
import math
import shutil
import subprocess
import time
import uuid
from collections import Counter
from datetime import datetime
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from rag.eval.retrieval_metrics import compute_retrieval_metrics
from rag.eval.manifest import PROJECT_ROOT, build_manifest


EVAL_ROOT = PROJECT_ROOT / "rag" / "eval"
ARTIFACT_ROOT = EVAL_ROOT / "artifacts"
DATASET_DIR = PROJECT_ROOT / "evaluation" / "EEMRS_Eval_V1" / "datasets"
AGENT_RESULT_ROOT = PROJECT_ROOT / "evaluation" / "EEMRS_Eval_V1" / "runner" / "results" / "baseline_v1"


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def load_retrieval_cases(dataset: str, split: str, limit: int | None) -> list[dict[str, Any]]:
    path = DATASET_DIR / f"{dataset}_{split}.jsonl"
    rows = read_jsonl(path)
    cases = []
    for row in rows[:limit]:
        case_id = row.get("metadata", {}).get("case_id") or row.get("case_id")
        query = row.get("initial_user_input", {}).get("text") or row.get("input", {}).get("text")
        if not case_id or not query:
            continue
        cases.append({
            "case_id": case_id,
            "query": query,
            "dataset": dataset,
            "split": split,
            "source_case_file": str(path.relative_to(PROJECT_ROOT)),
            "rag_ground_truth": row.get("rag_ground_truth") or {},
        })
    return cases


def post_json(url: str, payload: dict[str, Any], headers: dict[str, str], timeout: float) -> tuple[int, dict[str, Any]]:
    data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    request = Request(url, data=data, headers={**headers, "Content-Type": "application/json"}, method="POST")
    with urlopen(request, timeout=timeout) as response:
        body = response.read().decode("utf-8")
        return response.status, json.loads(body) if body else {}


def trace_row(
    case: dict[str, Any],
    payload: dict[str, Any],
    response: dict[str, Any] | None,
    latency_ms: int,
    trace_id: str,
    run_id: str,
    step_id: str,
    error: str | None,
) -> dict[str, Any]:
    chunks = response.get("chunks", []) if response else []
    trace_meta = response.get("trace_meta", {}) if response else {}
    return {
        "case_id": case["case_id"],
        "dataset": case["dataset"],
        "split": case["split"],
        "source_case_file": case["source_case_file"],
        "query_original": payload["query"],
        "query_expanded": response.get("expanded_query") if response else None,
        "scene": payload["scene"],
        "include_doc_types": payload.get("include_doc_types"),
        "top_k": payload["top_k"],
        "success": bool(response and response.get("success")),
        "http_status": response.get("_http_status") if response else None,
        "results": [
            {
                "rank": index,
                "chunk_id": chunk.get("chunk_id"),
                "doc_id": chunk.get("doc_id"),
                "doc_type": chunk.get("doc_type"),
                "score": chunk.get("score"),
                "final_score": chunk.get("final_score"),
                "title": chunk.get("title"),
                "source": chunk.get("source") or chunk.get("source_type"),
            }
            for index, chunk in enumerate(chunks, 1)
        ],
        "latency_ms": latency_ms,
        "trace_id": trace_meta.get("trace_id") or trace_id,
        "run_id": trace_meta.get("run_id") or run_id,
        "step_id": trace_meta.get("step_id") or step_id,
        "error_message": error or (response.get("error_message") if response else None),
        "retrieval_selection": trace_meta.get("retrieval_selection"),
        "rag_ground_truth_present": bool(case.get("rag_ground_truth")),
        "expected_knowledge_topics": case.get("rag_ground_truth", {}).get("expected_knowledge_topics", []),
    }


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


def retrieval_metrics(rows: list[dict[str, Any]]) -> dict[str, Any]:
    return compute_retrieval_metrics(rows)


def summarize(rows: list[dict[str, Any]]) -> dict[str, Any]:
    latencies = [row["latency_ms"] for row in rows if row.get("success")]
    doc_types = Counter()
    returned_counts = []
    for row in rows:
        returned_counts.append(len(row.get("results", [])))
        for result in row.get("results", []):
            doc_types[result.get("doc_type") or "unknown"] += 1
    total = len(rows)
    successes = sum(1 for row in rows if row.get("success"))
    return {
        "total_cases": total,
        "successful_cases": successes,
        "failed_cases": total - successes,
        "request_success_rate": successes / total if total else 0.0,
        "average_retrieval_latency_ms": sum(latencies) / len(latencies) if latencies else None,
        "p50_retrieval_latency_ms": percentile(latencies, 0.50),
        "p95_retrieval_latency_ms": percentile(latencies, 0.95),
        "average_returned_chunks": sum(returned_counts) / len(returned_counts) if returned_counts else 0.0,
        "doc_type_distribution": dict(sorted(doc_types.items())),
        "retrieval_metrics": retrieval_metrics(rows),
    }


def latest_agent_metrics_snapshot() -> dict[str, Any]:
    if not AGENT_RESULT_ROOT.exists():
        return {"available": False, "reason": "agent baseline result directory not found"}
    candidates = [path for path in AGENT_RESULT_ROOT.iterdir() if (path / "metrics_summary.json").exists()]
    if not candidates:
        return {"available": False, "reason": "no existing metrics_summary.json found"}
    latest = max(candidates, key=lambda path: (path / "metrics_summary.json").stat().st_mtime)
    return {
        "available": True,
        "source": str((latest / "metrics_summary.json").relative_to(PROJECT_ROOT)),
        "metrics_summary": json.loads((latest / "metrics_summary.json").read_text(encoding="utf-8")),
    }


def run_agent_harness(run_id: str, split: str, phase: str) -> dict[str, Any]:
    command = [
        "python",
        "-S",
        str(PROJECT_ROOT / "evaluation" / "EEMRS_Eval_V1" / "runner" / "tools" / "run_eval.py"),
        "--split",
        split,
        "--phase",
        phase,
        "--agent-mode",
        "http",
        "--run-id",
        run_id,
    ]
    started = time.time()
    result = subprocess.run(command, cwd=PROJECT_ROOT, capture_output=True, text=True)
    output_dir = AGENT_RESULT_ROOT / run_id
    snapshot = {
        "available": result.returncode == 0 and (output_dir / "metrics_summary.json").exists(),
        "command": command,
        "return_code": result.returncode,
        "elapsed_ms": int((time.time() - started) * 1000),
        "stdout_tail": result.stdout[-4000:],
        "stderr_tail": result.stderr[-4000:],
        "output_dir": str(output_dir.relative_to(PROJECT_ROOT)),
    }
    if (output_dir / "metrics_summary.json").exists():
        snapshot["metrics_summary"] = json.loads((output_dir / "metrics_summary.json").read_text(encoding="utf-8"))
    return snapshot


def write_readme(path: Path, run_id: str, summary: dict[str, Any], agent_snapshot: dict[str, Any]) -> None:
    lines = [
        "# Medical RAG V1 Dense Baseline",
        "",
        "This artifact freezes the current dense-only Medical RAG V1 retrieval behavior for later RAG V2 ablation and regression comparison. It is not a final RAG version.",
        "",
        f"- run_id: {run_id}",
        "- normal API contract: POST /rag/retrieve, unchanged",
        "- retrieval artifact: retrieval_results.jsonl",
        "- summary artifact: retrieval_summary.json",
        "- manifest artifact: baseline_manifest.json",
        f"- total retrieval cases: {summary['total_cases']}",
        f"- request success rate: {summary['request_success_rate']}",
        f"- agent metrics snapshot available: {agent_snapshot.get('available')}",
        "",
        "Retrieval metrics use only existing EEMRS Eval V1 rag_ground_truth.expected_knowledge_topics when present. No new gold labels were created.",
    ]
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--rag-base-url", default="http://127.0.0.1:18080")
    parser.add_argument("--dataset", default="consultation", choices=["consultation"])
    parser.add_argument("--split", default="dev", choices=["dev"])
    parser.add_argument("--limit", type=int, default=20)
    parser.add_argument("--scene", default="pre_inquiry")
    parser.add_argument("--top-k", type=int, default=8)
    parser.add_argument("--timeout-seconds", type=float, default=30)
    parser.add_argument("--run-agent-harness", action="store_true")
    parser.add_argument("--agent-phase", default="smoke", choices=["smoke", "consultation", "safety", "report", "doctor_draft", "all"])
    args = parser.parse_args()

    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    run_id = f"medical_rag_v1_dense_{timestamp}_{uuid.uuid4().hex[:8]}"
    output_dir = ARTIFACT_ROOT / run_id
    output_dir.mkdir(parents=True, exist_ok=False)

    cases = load_retrieval_cases(args.dataset, args.split, args.limit)
    rows = []
    url = args.rag_base_url.rstrip("/") + "/rag/retrieve"
    for case in cases:
        trace_id = f"{run_id}-{case['case_id']}"
        step_id = f"{case['case_id']}-retrieval"
        payload = {
            "query": case["query"],
            "top_k": args.top_k,
            "include_doc_types": None,
            "scene": args.scene,
        }
        start = time.time()
        response = None
        error = None
        try:
            status, response = post_json(
                url,
                payload,
                {
                    "X-Agent-Trace-Id": trace_id,
                    "X-Agent-Run-Id": run_id,
                    "X-Agent-Step-Id": step_id,
                },
                timeout=args.timeout_seconds,
            )
            response["_http_status"] = status
        except (HTTPError, URLError, TimeoutError, OSError) as exc:
            error = str(exc)
        latency_ms = int((time.time() - start) * 1000)
        rows.append(trace_row(case, payload, response, latency_ms, trace_id, run_id, step_id, error))

    manifest = build_manifest(run_id, include_live_collection=True)
    summary = summarize(rows)
    agent_snapshot = run_agent_harness(f"{run_id}_agent_{args.agent_phase}", args.split, args.agent_phase) if args.run_agent_harness else latest_agent_metrics_snapshot()

    (output_dir / "baseline_manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    (output_dir / "retrieval_results.jsonl").write_text("".join(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n" for row in rows), encoding="utf-8")
    (output_dir / "retrieval_summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    (output_dir / "agent_metrics_snapshot.json").write_text(json.dumps(agent_snapshot, ensure_ascii=False, indent=2), encoding="utf-8")
    write_readme(output_dir / "README.md", run_id, summary, agent_snapshot)

    root_manifest = EVAL_ROOT / "baseline_manifest.json"
    shutil.copyfile(output_dir / "baseline_manifest.json", root_manifest)
    print(json.dumps({"run_id": run_id, "artifact_path": str(output_dir), "summary": summary}, ensure_ascii=False, indent=2))
    return 0 if rows and summary["failed_cases"] == 0 else 2


if __name__ == "__main__":
    raise SystemExit(main())
