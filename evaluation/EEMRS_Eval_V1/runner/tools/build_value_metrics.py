from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

ROOT = Path(__file__).resolve().parents[2]
RUNNER_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(RUNNER_ROOT))

from core.event_log_loader import load_event_logs
from core.human_review_loader import load_human_reviews
from evaluators.product_value_evaluator import (
    EVENT_LOG_REQUIRED,
    NOT_OBSERVABLE,
    OBSERVED,
    aggregate_consultation_quality,
    aggregate_doctor_draft_quality,
    aggregate_doctor_draft_reviews,
    aggregate_event_metrics,
    aggregate_report_quality,
    aggregate_report_reviews,
    metric,
)


RESULTS_ROOT = RUNNER_ROOT / "results" / "baseline_v1"


def read_json(path: Path) -> Dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def read_case_results(path: Path) -> List[Dict[str, Any]]:
    case_path = path / "case_results.jsonl"
    if not case_path.exists():
        return []
    return [json.loads(line) for line in case_path.read_text(encoding="utf-8").splitlines() if line.strip()]


def discover_latest_run(dataset: str) -> Optional[Path]:
    if not RESULTS_ROOT.exists():
        return None
    candidates: List[Tuple[float, Path]] = []
    for path in RESULTS_ROOT.iterdir():
        if not path.is_dir():
            continue
        summary_path = path / "metrics_summary.json"
        if not summary_path.exists() or not (path / "case_results.jsonl").exists():
            continue
        try:
            summary = read_json(summary_path)
        except Exception:
            continue
        if dataset in summary.get("datasets", {}):
            candidates.append((path.stat().st_mtime, path))
    if not candidates:
        return None
    return sorted(candidates, key=lambda item: item[0], reverse=True)[0][1]


def rows_for_dataset(path: Optional[Path], dataset: str) -> Tuple[List[Dict[str, Any]], Dict[str, Any], str]:
    if path is None:
        return [], {}, ""
    rows = [row for row in read_case_results(path) if row.get("dataset") == dataset]
    summary = read_json(path / "metrics_summary.json")
    return rows, summary, path.name


def unavailable_layer1(dataset: str) -> Dict[str, Dict[str, Any]]:
    return {
        f"{dataset}_artifact_available": metric(
            f"{dataset} artifact available",
            None,
            NOT_OBSERVABLE,
            0,
            f"{dataset}_eval_artifact",
            "no existing eval artifact with metrics_summary.json was found",
            True,
            NOT_OBSERVABLE,
        )
    }


def build_summary(args: argparse.Namespace) -> Dict[str, Any]:
    consultation_run = Path(args.consultation_run) if args.consultation_run else discover_latest_run("consultation")
    report_run = Path(args.report_run) if args.report_run else discover_latest_run("report")
    doctor_draft_run = Path(args.doctor_draft_run) if args.doctor_draft_run else discover_latest_run("doctor_draft")

    consultation_rows, consultation_summary, consultation_run_id = rows_for_dataset(consultation_run, "consultation")
    report_rows, report_summary, report_run_id = rows_for_dataset(report_run, "report")
    doctor_rows, doctor_summary, doctor_run_id = rows_for_dataset(doctor_draft_run, "doctor_draft")

    reviews = load_human_reviews(ROOT)
    events = load_event_logs(ROOT)

    layer_1: Dict[str, Any] = {
        "consultation": aggregate_consultation_quality(consultation_rows, consultation_summary, consultation_run_id) if consultation_rows else unavailable_layer1("consultation"),
        "report": aggregate_report_quality(report_rows, report_summary, report_run_id) if report_rows else unavailable_layer1("report"),
        "doctor_draft": aggregate_doctor_draft_quality(doctor_rows, doctor_summary, doctor_run_id) if doctor_rows else unavailable_layer1("doctor_draft"),
    }
    layer_2: Dict[str, Any] = {
        "doctor_draft": aggregate_doctor_draft_reviews(reviews["doctor_draft"]),
        "report": aggregate_report_reviews(reviews["report"]),
        "consultation": {
            "pre_consultation_summary_acceptance_rate": metric("预问诊摘要采纳率", None, "REQUIRES_HUMAN", 0, "consultation_review", "doctor accepts AI pre-consultation summary / reviewed", True, "HUMAN_REVIEW")
        },
    }
    event_metrics = aggregate_event_metrics(events)
    layer_3: Dict[str, Any] = {
        "consultation_prep_time": {key: value for key, value in event_metrics.items() if key.startswith("consultation_prep_") or key in {"prep_time_saved", "prep_time_reduction_rate"}},
        "report_review_time": {key: value for key, value in event_metrics.items() if key.startswith("report_review_")},
        "draft_completion_time": {key: value for key, value in event_metrics.items() if key.startswith("draft_completion_") or key == "draft_time_reduction_rate"},
        "key_information": {key: value for key, value in event_metrics.items() if key.startswith("key_information_") or key == "time_to_first_key_information"},
    }

    return {
        "schema_version": 1,
        "benchmark": "EEMRS_Eval_V1",
        "notes": [
            "No new benchmark or DEV/Holdout case is created by this value metrics summary.",
            "Human adoption and timing metrics are null unless supplied by human_review or event_log sidecars.",
            "Offline proxy metrics must use *_proxy names and must not be presented as clinician adoption.",
        ],
        "source_runs": {
            "consultation": consultation_run_id or None,
            "report": report_run_id or None,
            "doctor_draft": doctor_run_id or None,
        },
        "layer_1_ai_quality": layer_1,
        "layer_2_human_ai_collaboration": layer_2,
        "layer_3_product_value": layer_3,
        "coverage": {
            "human_review_rows": {key: len(value) for key, value in reviews.items()},
            "event_log_rows": len(events),
            "strict_no_fake_human_metrics": True,
        },
    }


def metric_lines(metrics: Dict[str, Dict[str, Any]]) -> List[str]:
    lines = []
    for key, item in metrics.items():
        value = item.get("value")
        value_text = "null" if value is None else str(value)
        lines.append(f"- {key}: {value_text} ({item.get('status')}, n={item.get('sample_count')}, evidence={item.get('evidence_level')})")
    return lines


def build_report(summary: Dict[str, Any]) -> str:
    lines = [
        "# EEMRS 三层 Agent 产品价值评测报告",
        "",
        "## 一、AI 能力指标",
    ]
    for domain, metrics in summary["layer_1_ai_quality"].items():
        lines.append(f"### {domain}")
        lines.extend(metric_lines(metrics))
        lines.append("")
    lines.extend(["## 二、人机协同指标"])
    for domain, metrics in summary["layer_2_human_ai_collaboration"].items():
        lines.append(f"### {domain}")
        lines.extend(metric_lines(metrics))
        lines.append("")
    lines.extend(["## 三、产品价值指标"])
    for domain, metrics in summary["layer_3_product_value"].items():
        lines.append(f"### {domain}")
        lines.extend(metric_lines(metrics))
        lines.append("")
    lines.extend([
        "## 四、当前不可观测指标",
        "- 人工采纳、直接采纳、医生纠错、关键发现采纳：没有 human_review sidecar 时保持 REQUIRES_HUMAN。",
        "- 接诊准备时间、报告阅读时间、病历书写时间、重点信息查看率：没有 events sidecar 时保持 EVENT_LOG_REQUIRED。",
        "- evidence_grounding_rate、异常变化召回/精确率：当前 artifact 缺少稳定 claim-level 证据或异常变化分母，保持 NOT_OBSERVABLE。",
        "",
        "## 五、样本量与证据等级",
        f"- Consultation source_run_id: {summary['source_runs'].get('consultation')}",
        f"- Report source_run_id: {summary['source_runs'].get('report')}",
        f"- Doctor Draft source_run_id: {summary['source_runs'].get('doctor_draft')}",
        f"- Human review rows: {summary['coverage']['human_review_rows']}",
        f"- Event log rows: {summary['coverage']['event_log_rows']}",
        "",
        "## 严格确认",
        "- 新增独立测试集：NO",
        "- 修改 frozen benchmark：NO",
        "- 修改 Holdout：NO",
        "- 调用真实 DeepSeek：NO",
        "- 伪造人工采纳数据：NO",
        "- 伪造时间数据：NO",
    ])
    return "\n".join(lines) + "\n"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build EEMRS three-layer value metrics from existing artifacts and sidecars.")
    parser.add_argument("--consultation-run", default="", help="Existing consultation run directory")
    parser.add_argument("--report-run", default="", help="Existing report run directory")
    parser.add_argument("--doctor-draft-run", default="", help="Existing doctor_draft run directory")
    parser.add_argument("--output-dir", default=str(ROOT / "runner" / "results" / "value_metrics"), help="Output directory")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    summary = build_summary(args)
    (output_dir / "value_metrics_summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    (output_dir / "value_metrics_report.md").write_text(build_report(summary), encoding="utf-8")
    print(json.dumps({"output_dir": str(output_dir), "source_runs": summary["source_runs"]}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
