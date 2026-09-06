"""Compare current /rag/retrieve responses with a saved baseline artifact."""

from __future__ import annotations

import argparse
import json
import time
from datetime import datetime
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from rag.eval.manifest import PROJECT_ROOT


COMPARISON_ROOT = PROJECT_ROOT / "rag" / "eval" / "comparisons"


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def post_json(url: str, payload: dict[str, Any], headers: dict[str, str], timeout: float) -> tuple[int | None, dict[str, Any] | None, str | None]:
    data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    request = Request(url, data=data, headers={**headers, "Content-Type": "application/json"}, method="POST")
    try:
        with urlopen(request, timeout=timeout) as response:
            body = response.read().decode("utf-8")
            return response.status, json.loads(body) if body else {}, None
    except (HTTPError, URLError, TimeoutError, OSError) as exc:
        return None, None, str(exc)


def normalize_response(row: dict[str, Any], response: dict[str, Any] | None, error: str | None) -> dict[str, Any]:
    chunks = response.get("chunks", []) if response else []
    return {
        "success": bool(response and response.get("success")),
        "query_original": row.get("query_original"),
        "query_expanded": response.get("expanded_query") if response else None,
        "error_message": error or (response.get("error_message") if response else row.get("error_message")),
        "returned_chunk_count": len(chunks),
        "chunk_id_sequence": [chunk.get("chunk_id") for chunk in chunks],
        "doc_type_sequence": [chunk.get("doc_type") for chunk in chunks],
        "scores": [chunk.get("score") for chunk in chunks],
        "final_scores": [chunk.get("final_score") for chunk in chunks],
    }


def normalize_baseline(row: dict[str, Any]) -> dict[str, Any]:
    results = row.get("results", [])
    return {
        "success": bool(row.get("success")),
        "query_original": row.get("query_original"),
        "query_expanded": row.get("query_expanded"),
        "error_message": row.get("error_message"),
        "returned_chunk_count": len(results),
        "chunk_id_sequence": [item.get("chunk_id") for item in results],
        "doc_type_sequence": [item.get("doc_type") for item in results],
        "scores": [item.get("score") for item in results],
        "final_scores": [item.get("final_score") for item in results],
    }


def same_scores(left: list[Any], right: list[Any], tolerance: float) -> bool:
    if len(left) != len(right):
        return False
    for a, b in zip(left, right):
        if a is None or b is None:
            if a != b:
                return False
            continue
        if abs(float(a) - float(b)) > tolerance:
            return False
    return True


def compare_rows(baseline: dict[str, Any], current: dict[str, Any], tolerance: float) -> dict[str, Any]:
    ranking_match = baseline["chunk_id_sequence"] == current["chunk_id_sequence"]
    doc_type_match = baseline["doc_type_sequence"] == current["doc_type_sequence"]
    score_match = same_scores(baseline["scores"], current["scores"], tolerance)
    final_score_match = same_scores(baseline["final_scores"], current["final_scores"], tolerance)
    exact_match = baseline == current or (
        baseline["success"] == current["success"]
        and baseline["query_expanded"] == current["query_expanded"]
        and baseline["returned_chunk_count"] == current["returned_chunk_count"]
        and ranking_match
        and doc_type_match
        and score_match
        and final_score_match
    )
    return {
        "exact_match": exact_match,
        "ranking_match": ranking_match,
        "doc_type_match": doc_type_match,
        "score_match": score_match,
        "final_score_match": final_score_match,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline-dir", required=True)
    parser.add_argument("--rag-base-url", default="http://127.0.0.1:18080")
    parser.add_argument("--timeout-seconds", type=float, default=30)
    parser.add_argument("--score-tolerance", type=float, default=1e-6)
    args = parser.parse_args()

    baseline_dir = Path(args.baseline_dir)
    if not baseline_dir.is_absolute():
        baseline_dir = PROJECT_ROOT / baseline_dir
    rows = read_jsonl(baseline_dir / "retrieval_results.jsonl")
    run_id = f"baseline_comparison_{datetime.now().strftime('%Y%m%d_%H%M%S')}"
    output_dir = COMPARISON_ROOT / run_id
    output_dir.mkdir(parents=True, exist_ok=False)
    url = args.rag_base_url.rstrip("/") + "/rag/retrieve"

    comparisons = []
    for row in rows:
        payload = {
            "query": row.get("query_original"),
            "top_k": row.get("top_k"),
            "include_doc_types": row.get("include_doc_types"),
            "scene": row.get("scene"),
        }
        start = time.time()
        status, response, error = post_json(
            url,
            payload,
            {
                "X-Agent-Trace-Id": row.get("trace_id") or "",
                "X-Agent-Run-Id": row.get("run_id") or "",
                "X-Agent-Step-Id": row.get("step_id") or "",
            },
            timeout=args.timeout_seconds,
        )
        baseline_norm = normalize_baseline(row)
        current_norm = normalize_response(row, response, error)
        comparison = compare_rows(baseline_norm, current_norm, args.score_tolerance)
        comparisons.append({
            "case_id": row.get("case_id"),
            "http_status": status,
            "latency_ms": int((time.time() - start) * 1000),
            "comparison": comparison,
            "baseline": baseline_norm,
            "current": current_norm,
        })

    summary = {
        "total_cases": len(comparisons),
        "exact_match_cases": sum(1 for row in comparisons if row["comparison"]["exact_match"]),
        "mismatch_cases": sum(1 for row in comparisons if not row["comparison"]["exact_match"]),
        "ranking_mismatch_cases": sum(1 for row in comparisons if not row["comparison"]["ranking_match"]),
        "score_mismatch_cases": sum(
            1
            for row in comparisons
            if not row["comparison"]["score_match"] or not row["comparison"]["final_score_match"]
        ),
        "baseline_dir": str(baseline_dir),
        "rag_base_url": args.rag_base_url,
        "score_tolerance": args.score_tolerance,
    }
    (output_dir / "comparison_summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    (output_dir / "comparison_cases.jsonl").write_text(
        "".join(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n" for row in comparisons),
        encoding="utf-8",
    )
    print(json.dumps({"artifact_path": str(output_dir), "summary": summary}, ensure_ascii=False, indent=2))
    return 0 if summary["mismatch_cases"] == 0 else 2


if __name__ == "__main__":
    raise SystemExit(main())
