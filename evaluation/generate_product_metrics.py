#!/usr/bin/env python3
"""Generate product-facing metrics from existing evaluation artifacts.

The script only aggregates values that are present in repository outputs. Missing
or unlabeled metrics are reported as "Not available / 尚无足够真实数据".
"""

from __future__ import annotations

import csv
import json
import re
import subprocess
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from statistics import mean
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
OUT_DIR = ROOT / "evaluation" / "results" / "product_metrics"
NA = "Not available / 尚无足够真实数据"


@dataclass
class Metric:
    area: str
    name: str
    value: Any
    source: str
    sample_count: Any
    method: str
    note: str = ""


def main() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    generated_at = datetime.now().isoformat(timespec="seconds")
    commit = git_commit()
    metrics: list[Metric] = []
    metadata = {
        "generated_at": generated_at,
        "git_commit": commit,
        "model": "deepseek-v4-flash where captured in eval output; mock-model for docs/eval mock harness",
        "rag": "RAG comparison uses medical_rag_ab_test_report_20260606_102121.md; pre-consultation batch output includes live response payloads but not a uniform RAG flag per case.",
    }
    metrics.extend(preconsultation_metrics())
    metrics.extend(rag_metrics())
    metrics.extend(report_trend_metrics())
    metrics.extend(draft_review_metrics())

    rows = [metric.__dict__ for metric in metrics]
    payload = {"metadata": metadata, "metrics": rows}
    (OUT_DIR / "product_metrics.json").write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    write_csv(OUT_DIR / "product_metrics.csv", rows)
    (OUT_DIR / "product_metrics.md").write_text(markdown(metadata, metrics), encoding="utf-8")
    print(f"Wrote {OUT_DIR / 'product_metrics.json'}")
    print(f"Wrote {OUT_DIR / 'product_metrics.csv'}")
    print(f"Wrote {OUT_DIR / 'product_metrics.md'}")


def preconsultation_metrics() -> list[Metric]:
    path = ROOT / "evaluation" / "reports" / "eval_report_extended_refined_20260603_223235.json"
    data = json.loads(path.read_text(encoding="utf-8"))
    n = len(data)
    successful = [case for case in data if final_data(case).get("success") is True]
    rounds = [num(final_data(case).get("round")) for case in data if num(final_data(case).get("round")) is not None]
    must_coverages = [
        safe_ratio(case.get("mustAskHitCount"), case.get("mustAskTotal"))
        for case in data
        if safe_ratio(case.get("mustAskHitCount"), case.get("mustAskTotal")) is not None
    ]
    key_omissions = [
        1 - safe_ratio(case.get("mustAskHitCount"), case.get("mustAskTotal"))
        for case in data
        if safe_ratio(case.get("mustAskHitCount"), case.get("mustAskTotal")) is not None
    ]
    red_flag_cases = [case for case in data if "red_flag" in str(case.get("caseType", ""))]
    red_flag_hits = [case for case in red_flag_cases if case.get("hardFailHits") == [] and final_data(case).get("urgency") in {"emergency", "urgent"}]
    department_cases = [case for case in data if case.get("departmentHits") is not None]
    department_hits = [case for case in department_cases if len(case.get("departmentHits") or []) > 0]
    json_cases = [case for case in data if case.get("jsonValid") is not None]
    model_failures = [case for case in data if final_data(case).get("success") is False or final_data(case).get("error")]
    source = rel(path)
    return [
        Metric("pre_consultation", "评测样本数量", n, source, n, "JSON list length"),
        Metric("pre_consultation", "成功完成率", pct(len(successful), n), source, n, "finalResponse.data.success == true"),
        Metric("pre_consultation", "平均问诊轮次", round(mean(rounds), 4) if rounds else NA, source, len(rounds), "average finalResponse.data.round"),
        Metric("pre_consultation", "平均响应时间", NA, source, n, "historical JSON does not include per-case latency"),
        Metric("pre_consultation", "Must-Ask 平均覆盖率", round(mean(must_coverages), 4) if must_coverages else NA, source, len(must_coverages), "mean(mustAskHitCount / mustAskTotal)"),
        Metric("pre_consultation", "关键病史遗漏率", round(mean(key_omissions), 4) if key_omissions else NA, source, len(key_omissions), "mean(1 - mustAsk coverage)"),
        Metric("pre_consultation", "red flag 召回率", pct(len(red_flag_hits), len(red_flag_cases)) if red_flag_cases else NA, source, len(red_flag_cases), "red_flag case with urgent/emergency final urgency and no hard fail"),
        Metric("pre_consultation", "科室推荐命中率", pct(len(department_hits), len(department_cases)) if department_cases else NA, source, len(department_cases), "departmentHits non-empty"),
        Metric("pre_consultation", "输出格式成功率", pct(sum(1 for c in json_cases if c.get("jsonValid") is True), len(json_cases)) if json_cases else NA, source, len(json_cases), "jsonValid == true; N/A when case is not record JSON task"),
        Metric("pre_consultation", "模型调用失败率", pct(len(model_failures), n), source, n, "finalResponse.data.success == false or error present"),
    ]


def rag_metrics() -> list[Metric]:
    path = ROOT / "evaluation" / "reports" / "medical_rag_ab_test_report_20260606_102121.md"
    text = path.read_text(encoding="utf-8")
    unique_cases = match_number(text, r"去重后唯一 case 数：(\d+)")
    no_rag = match_number(text, r"无 RAG 平均总分：([0-9.]+)")
    with_rag = match_number(text, r"有 RAG 平均总分：([0-9.]+)")
    delta = match_number(text, r"平均分变化：([0-9.\-]+)")
    service_ok = match_text(text, r"RAG 服务可访问：([^\n]+)")
    milvus_ok = match_text(text, r"Milvus collection 存在：([^\n]+)")
    source = rel(path)
    retrieval_success = True if service_ok == "True" and milvus_ok == "True" else None
    return [
        Metric("rag", "RAG 对照样本数量", unique_cases or NA, source, unique_cases or NA, "parsed latest RAG A/B markdown"),
        Metric("rag", "RAG 检索成功率", pct(1, 1) if retrieval_success else NA, source, unique_cases or NA, "RAG service reachable and Milvus collection exists in report"),
        Metric("rag", "有效知识召回率", NA, source, unique_cases or NA, "no human-labeled relevant chunk set in current output"),
        Metric("rag", "空召回率", NA, source, unique_cases or NA, "latest markdown does not expose per-case empty retrieval counts"),
        Metric("rag", "无关片段率", NA, source, unique_cases or NA, "no human-labeled irrelevant chunk set in current output"),
        Metric("rag", "RAG 开启总分", with_rag if with_rag is not None else NA, source, unique_cases or NA, "reported average score with RAG"),
        Metric("rag", "RAG 关闭总分", no_rag if no_rag is not None else NA, source, unique_cases or NA, "reported average score without RAG"),
        Metric("rag", "RAG 开启/关闭总分变化", delta if delta is not None else NA, source, unique_cases or NA, "with_rag_score - no_rag_score"),
        Metric("rag", "RAG 开启与关闭 Must-Ask 覆盖率变化", NA, source, unique_cases or NA, "RAG A/B report stores total score, not must-ask deltas"),
        Metric("rag", "RAG 开启与关闭安全指标变化", NA, source, unique_cases or NA, "RAG A/B report stores total score, not safety deltas"),
        Metric("rag", "平均检索耗时", NA, source, unique_cases or NA, "historical report does not expose retrieval latency"),
    ]


def report_trend_metrics() -> list[Metric]:
    path = ROOT / "docs" / "eval" / "report_trend_eval_results.csv"
    rows = read_csv(path)
    n = len(rows)
    return [
        Metric("report_trend", "指标提取成功率", bool_rate(rows, "abnormal_detection_correct"), rel(path), n, "mean(abnormal_detection_correct)", "mock runner / 测试环境数据"),
        Metric("report_trend", "同名指标对齐成功率", NA, rel(path), n, "not separately emitted by current harness", "mock runner / 测试环境数据"),
        Metric("report_trend", "趋势判断成功率", bool_rate(rows, "trend_direction_correct"), rel(path), n, "mean(trend_direction_correct)", "mock runner / 测试环境数据"),
        Metric("report_trend", "脱敏成功率", bool_rate(rows, "privacy_pass"), rel(path), n, "mean(privacy_pass)", "mock runner / 测试环境数据"),
        Metric("report_trend", "分析接口成功率", pct(sum(1 for r in rows if r.get("status") == "PASSED"), n), rel(path), n, "status == PASSED", "mock runner / 测试环境数据"),
        Metric("report_trend", "平均处理时延", round(mean([float(r["latency_ms"]) for r in rows if r.get("latency_ms")]), 4), rel(path), n, "average latency_ms", "mock runner / 测试环境数据"),
    ]


def draft_review_metrics() -> list[Metric]:
    source = "agent_medical_record_draft_audit table / no production export found"
    names = ["完全采纳率", "部分采纳率", "拒绝率", "写入正式病历成功率", "平均审核时长", "平均修改字段数", "字段修改率", "常见拒绝原因", "最常被修改的字段"]
    return [Metric("draft_review", name, NA, source, 0, "requires real audit rows after doctors review drafts") for name in names]


def final_data(case: dict[str, Any]) -> dict[str, Any]:
    return (((case.get("rawResponse") or {}).get("finalResponse") or {}).get("data") or {})


def safe_ratio(a: Any, b: Any) -> float | None:
    try:
        den = float(b)
        return None if den == 0 else float(a) / den
    except Exception:
        return None


def pct(num_value: int, den_value: int) -> float | str:
    return NA if den_value == 0 else round(num_value / den_value, 4)


def bool_rate(rows: list[dict[str, str]], field: str) -> float | str:
    values = [r.get(field) for r in rows if r.get(field) != ""]
    if not values:
        return NA
    return round(sum(1 for v in values if str(v).lower() == "true") / len(values), 4)


def num(value: Any) -> float | None:
    try:
        return float(value)
    except Exception:
        return None


def match_number(text: str, pattern: str) -> float | int | None:
    value = match_text(text, pattern)
    if value is None or value == "None":
        return None
    parsed = float(value)
    return int(parsed) if parsed.is_integer() else parsed


def match_text(text: str, pattern: str) -> str | None:
    m = re.search(pattern, text)
    return m.group(1).strip() if m else None


def read_csv(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8-sig", newline="") as f:
        return list(csv.DictReader(f))


def write_csv(path: Path, rows: list[dict[str, Any]]) -> None:
    with path.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=["area", "name", "value", "source", "sample_count", "method", "note"])
        writer.writeheader()
        writer.writerows(rows)


def markdown(metadata: dict[str, str], metrics: list[Metric]) -> str:
    lines = ["# 产品指标汇总", ""]
    for key, value in metadata.items():
        lines.append(f"- {key}: {value}")
    lines.extend(["", "| 模块 | 指标 | 数值 | 样本数 | 数据来源 |", "|---|---:|---:|---:|---|"])
    for metric in metrics:
        lines.append(f"| {metric.area} | {metric.name} | {metric.value} | {metric.sample_count} | `{metric.source}` |")
    lines.extend([
        "",
        "## 说明",
        "",
        "- 所有不可获得的指标均显式标记为 `Not available / 尚无足够真实数据`，不做推断。",
        "- `docs/eval` 中的 report_trend 行来自 mock runner，不能描述为生产医生使用数据。",
    ])
    return "\n".join(lines) + "\n"


def rel(path: Path) -> str:
    return str(path.relative_to(ROOT)).replace("\\", "/")


def git_commit() -> str:
    try:
        return subprocess.check_output(["git", "rev-parse", "--short", "HEAD"], cwd=ROOT, text=True).strip()
    except Exception:
        return NA


if __name__ == "__main__":
    main()
