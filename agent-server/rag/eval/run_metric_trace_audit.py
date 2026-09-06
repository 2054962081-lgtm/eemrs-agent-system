"""Audit retrieval metrics on frozen result files and emit trace completeness artifacts."""

from __future__ import annotations

import argparse
import json
from datetime import datetime
from pathlib import Path
from typing import Any

from rag.eval.retrieval_metrics import GOLD_FIELD, MATCH_FIELD, compute_case_metric, compute_retrieval_metrics


PROJECT_ROOT = Path(__file__).resolve().parents[2]
ARTIFACT_ROOT = PROJECT_ROOT / "rag" / "eval" / "artifacts"
DEFAULT_FROZEN = ARTIFACT_ROOT / "retrieval_ablation_rerank_20260905_173023"


def read_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True), encoding="utf-8")


def write_jsonl(path: Path, rows: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("".join(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n" for row in rows), encoding="utf-8")


def modes_from_frozen(frozen_dir: Path) -> dict[str, Path]:
    return {
        "dense": frozen_dir / "dense_results.jsonl",
        "bm25": frozen_dir / "bm25_results.jsonl",
        "hybrid_rrf": frozen_dir / "hybrid_rrf_results.jsonl",
        "hybrid_rerank": frozen_dir / "hybrid_rerank_results.jsonl",
    }


def metric_definition() -> dict[str, Any]:
    return {
        "gold_source": GOLD_FIELD,
        "gold_structure": "list[str] of expected knowledge document ids",
        "gold_cardinality": "one or more doc_ids per labeled case",
        "matching_field": MATCH_FIELD,
        "relevance_type": "binary",
        "relevance_matcher": "retrieved_item.doc_id in normalized expected_knowledge_topics",
        "recall_at_k": "unique relevant gold doc_ids retrieved in top K / total gold doc_ids",
        "hit_at_k": "at least one relevant result in top K; emitted only as case-level companion metric",
        "mrr": "mean reciprocal rank over all labeled cases; miss contributes 0",
        "ndcg_at_5": "binary DCG@5 divided by binary IDCG@5 using min(gold_count, 5) ideal relevant slots",
        "unlabeled_policy": "cases without expected_knowledge_topics are excluded from aggregate quality metrics",
    }


def extract_before_metrics(frozen_dir: Path) -> dict[str, dict[str, Any]]:
    comparison_path = frozen_dir / "comparison.json"
    if not comparison_path.exists():
        return {}
    comparison = read_json(comparison_path)
    before = {}
    for mode in modes_from_frozen(frozen_dir):
        metrics = ((comparison.get(mode) or {}).get("retrieval_metrics") or {})
        before[mode] = {
            "Recall@5": metrics.get("Recall@5"),
            "Recall@10": metrics.get("Recall@10"),
            "MRR": metrics.get("MRR"),
            "nDCG@5": metrics.get("nDCG@5"),
        }
    return before


def recompute_metrics(frozen_dir: Path, output_dir: Path) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    before = extract_before_metrics(frozen_dir)
    after: dict[str, dict[str, Any]] = {}
    case_rows: list[dict[str, Any]] = []
    affected_cases: list[dict[str, Any]] = []
    for mode, path in modes_from_frozen(frozen_dir).items():
        rows = read_jsonl(path)
        after_metrics = compute_retrieval_metrics(rows)
        after[mode] = {
            "Recall@5": after_metrics.get("Recall@5"),
            "Recall@10": after_metrics.get("Recall@10"),
            "MRR": after_metrics.get("MRR"),
            "nDCG@5": after_metrics.get("nDCG@5"),
        }
        for row in rows:
            metric = compute_case_metric(row).to_dict()
            metric["mode"] = mode
            case_rows.append(metric)
        for name, value in after[mode].items():
            old_value = (before.get(mode) or {}).get(name)
            if not numerically_equal(old_value, value):
                affected_cases.append({"mode": mode, "metric": name, "before": old_value, "after": value})
    write_jsonl(output_dir / "case_metric_details.jsonl", case_rows)
    comparison = {
        "frozen_source": str(frozen_dir),
        "original_metric_bug": bool(affected_cases),
        "bug_assessment": "Metric implementation verified." if not affected_cases else "Aggregate metric values changed after unified recomputation.",
        "before": before,
        "after": after,
        "differences": affected_cases,
    }
    write_json(output_dir / "before_after_metrics.json", comparison)
    return comparison, case_rows


def numerically_equal(left: Any, right: Any) -> bool:
    if left is None or right is None:
        return left is right
    try:
        return abs(float(left) - float(right)) < 1e-12
    except (TypeError, ValueError):
        return left == right


def representative_cases(case_rows: list[dict[str, Any]]) -> dict[str, Any]:
    dense = [row for row in case_rows if row["mode"] == "dense"]
    labeled = [row for row in case_rows if row.get("labeled")]
    return {
        "rank1_hit": next((row for row in dense if row["first_relevant_rank"] == 1), None)
        or next((row for row in labeled if row["first_relevant_rank"] == 1), None),
        "rank_gt1_hit": next((row for row in dense if row["first_relevant_rank"] and row["first_relevant_rank"] > 1), None)
        or next((row for row in labeled if row["first_relevant_rank"] and row["first_relevant_rank"] > 1), None),
        "miss": next((row for row in dense if row["first_relevant_rank"] is None), None)
        or next((row for row in labeled if row["first_relevant_rank"] is None), None),
    }


def trace_gap_map() -> dict[str, Any]:
    return {
        "before": {
            "RetrievalService": "candidates available in trace_meta for hybrid modes; dense has selection diagnostics",
            "FastAPI": "trace_meta returned without public contract change",
            "Java RagRetrievalClient": "trace_meta deserialized for retrieveWithMetadata; retrieve() drops metadata",
            "Consultation Agent": "query and retrieval chunks visible; final formatted RAG context not separately visible",
            "Doctor Draft Agent": "used retrieve(), so retrieval trace and final context were not observable",
            "Harness Artifact": "stores raw trace steps when available but had no compact retrieval trace export",
        },
        "after_target": {
            "Consultation Agent": "records selected chunks, final_context_chunk_order, context_transform, and final_context hash/length",
            "Doctor Draft Agent": "records retrieveWithMetadata trace, selected chunks, final_context_chunk_order, and retrieval_used",
            "Harness Artifact": "audit script can export retrieval_traces.jsonl, examples, and completeness summary from trace detail files",
        },
    }


def extract_step_payload(step: dict[str, Any]) -> dict[str, Any]:
    parsed: dict[str, Any] = {}
    for key in ("metadataJson", "responsePayloadJson", "requestPayloadJson"):
        value = step.get(key)
        if not value:
            continue
        try:
            loaded = json.loads(value)
            if isinstance(loaded, dict):
                parsed[key] = loaded
            else:
                parsed[key] = {"value": loaded}
        except Exception:
            parsed[key] = {"raw": value}
    return parsed


def collect_trace_artifacts(agent_output_dir: Path | None, trace_output_dir: Path) -> dict[str, Any]:
    trace_output_dir.mkdir(parents=True, exist_ok=True)
    if not agent_output_dir or not agent_output_dir.exists():
        empty = {
            "consultation": empty_trace_summary(),
            "doctor_draft": empty_trace_summary(),
            "note": "No live agent output directory was provided or found.",
        }
        write_json(trace_output_dir / "trace_completeness_summary.json", empty)
        write_jsonl(trace_output_dir / "retrieval_traces.jsonl", [])
        write_jsonl(trace_output_dir / "consultation_trace_examples.jsonl", [])
        write_jsonl(trace_output_dir / "doctor_draft_trace_examples.jsonl", [])
        return empty

    case_results = read_jsonl(agent_output_dir / "case_results.jsonl") if (agent_output_dir / "case_results.jsonl").exists() else []
    traces: list[dict[str, Any]] = []
    examples: dict[str, list[dict[str, Any]]] = {"consultation": [], "doctor_draft": []}
    summary = {
        "consultation": empty_trace_summary(),
        "doctor_draft": empty_trace_summary(),
    }
    for row in case_results:
        dataset = row.get("dataset")
        if dataset not in summary:
            continue
        summary[dataset]["total_cases"] += 1
        summary[dataset]["cases_with_agent_result"] += 1 if row.get("model_output") else 0
        summary[dataset]["cases_with_evaluation_result"] += 1 if row.get("evaluation") else 0
        summary[dataset]["cases_with_trace_id"] += 1 if row.get("agent_run_id") else 0
        detail_path = agent_output_dir / "traces" / f"{row.get('case_id')}_trace_detail.json"
        detail = read_json(detail_path) if detail_path.exists() else None
        retrieval_steps = []
        if isinstance(detail, dict):
            for step in detail.get("steps") or []:
                if "RAG" in str(step.get("stepType", "")):
                    payload = extract_step_payload(step)
                    retrieval_steps.append({"step": step, "payload": payload})
                    traces.append({
                        "case_id": row.get("case_id"),
                        "dataset": dataset,
                        "agent_run_id": row.get("agent_run_id"),
                        "step_id": step.get("stepId") or step.get("step_id"),
                        "step_type": step.get("stepType"),
                        "step_name": step.get("stepName"),
                        "status": step.get("status"),
                        "metadata": payload.get("metadataJson"),
                        "response": payload.get("responsePayloadJson"),
                    })
        has_retrieval = bool(retrieval_steps)
        has_selected = any("selected_chunks" in str(item) or "final_context_chunk_order" in str(item) for item in retrieval_steps)
        has_context = any("final_context" in str(item) or "context_transform" in str(item) for item in retrieval_steps)
        summary[dataset]["rag_expected_cases"] += 1
        summary[dataset]["cases_with_retrieval_trace"] += 1 if has_retrieval else 0
        summary[dataset]["cases_with_selected_chunks"] += 1 if has_selected else 0
        summary[dataset]["cases_with_final_context"] += 1 if has_context else 0
        if len(examples[dataset]) < 5:
            examples[dataset].append({
                "case_id": row.get("case_id"),
                "agent_run_id": row.get("agent_run_id"),
                "trace_status": row.get("trace_status"),
                "retrieval_trace_ref_count": len(retrieval_steps),
                "evaluation": row.get("evaluation"),
                "retrieval_trace_refs": [
                    {
                        "step_type": item["step"].get("stepType"),
                        "step_name": item["step"].get("stepName"),
                        "step_id": item["step"].get("stepId") or item["step"].get("step_id"),
                    }
                    for item in retrieval_steps
                ],
            })
    for data in summary.values():
        fill_rates(data)
    write_jsonl(trace_output_dir / "retrieval_traces.jsonl", traces)
    write_jsonl(trace_output_dir / "consultation_trace_examples.jsonl", examples["consultation"])
    write_jsonl(trace_output_dir / "doctor_draft_trace_examples.jsonl", examples["doctor_draft"])
    write_json(trace_output_dir / "trace_completeness_summary.json", summary)
    return summary


def empty_trace_summary() -> dict[str, Any]:
    return {
        "total_cases": 0,
        "rag_expected_cases": 0,
        "cases_with_trace_id": 0,
        "cases_with_retrieval_trace": 0,
        "cases_with_selected_chunks": 0,
        "cases_with_final_context": 0,
        "cases_with_agent_result": 0,
        "cases_with_evaluation_result": 0,
    }


def fill_rates(data: dict[str, Any]) -> None:
    denom = data.get("rag_expected_cases") or 0
    for key in ("retrieval_trace", "selected_chunks", "final_context"):
        count_key = f"cases_with_{key}"
        data[f"{key}_rate"] = data[count_key] / denom if denom else None
    all_cases = data.get("total_cases") or 0
    data["trace_id_rate"] = data["cases_with_trace_id"] / all_cases if all_cases else None


def report_text(metric_comparison: dict[str, Any], case_rows: list[dict[str, Any]], trace_summary: dict[str, Any]) -> str:
    reps = representative_cases(case_rows)
    lines = [
        "# Medical RAG Phase 2.5 Metric and Trace Audit",
        "",
        "## Metric Dependency Map",
        "",
        "case -> rag_ground_truth -> expected_knowledge_topics -> retrieved results -> doc_id relevance matcher -> Recall/MRR/nDCG",
        "",
        "## Metric Verification",
        "",
        f"- original_metric_bug: {metric_comparison['original_metric_bug']}",
        f"- assessment: {metric_comparison['bug_assessment']}",
        "",
        "## Representative Cases",
        "",
    ]
    for name, row in reps.items():
        if row:
            lines.append(f"- {name}: {row['case_id']} first_rank={row['first_relevant_rank']} rr={row['reciprocal_rank']} recall5={row['recall_at_5']} ndcg5={row['ndcg_at_5']}")
    lines.extend([
        "",
        "## Trace Gap Map",
        "",
        json.dumps(trace_gap_map(), ensure_ascii=False, indent=2),
        "",
        "## Trace Completeness",
        "",
        json.dumps(trace_summary, ensure_ascii=False, indent=2),
        "",
    ])
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--frozen-dir", type=Path, default=DEFAULT_FROZEN)
    parser.add_argument("--agent-output-dir", type=Path, default=None)
    args = parser.parse_args()

    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    output_dir = ARTIFACT_ROOT / f"rag_metric_trace_audit_{timestamp}"
    metric_dir = output_dir / "metric_audit"
    trace_dir = output_dir / "trace"
    metric_dir.mkdir(parents=True, exist_ok=False)
    trace_dir.mkdir(parents=True, exist_ok=True)

    write_json(metric_dir / "metric_definition.json", metric_definition())
    metric_comparison, case_rows = recompute_metrics(args.frozen_dir, metric_dir)
    trace_summary = collect_trace_artifacts(args.agent_output_dir, trace_dir)
    (metric_dir / "report.md").write_text(report_text(metric_comparison, case_rows, trace_summary), encoding="utf-8")
    (output_dir / "README.md").write_text(
        "# RAG Metric and Trace Audit\n\n"
        "Metrics are recomputed from frozen retrieval result files. Trace artifacts are extracted from a provided Agent Harness output directory when available.\n",
        encoding="utf-8",
    )
    print(json.dumps({"artifact_path": str(output_dir), "metric_bug": metric_comparison["original_metric_bug"], "trace_summary": trace_summary}, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
