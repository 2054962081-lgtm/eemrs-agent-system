#!/usr/bin/env python3
"""Check representative bad-case regressions against existing real eval output."""

from __future__ import annotations

import json
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CASES = ROOT / "evaluation" / "data" / "bad_case_regression_cases.jsonl"
RESULTS = ROOT / "evaluation" / "reports" / "eval_report_extended_refined_20260603_223235.json"
OUT_DIR = ROOT / "evaluation" / "results" / "bad_cases"


def main() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    cases = [json.loads(line) for line in CASES.read_text(encoding="utf-8").splitlines() if line.strip()]
    results = {item["caseId"]: item for item in json.loads(RESULTS.read_text(encoding="utf-8"))}
    checked = []
    for case in cases:
        result = results.get(case["caseId"], {})
        must_total = int(result.get("mustAskTotal") or 0)
        must_hit = int(result.get("mustAskHitCount") or 0)
        hard_fail_hits = result.get("hardFailHits") or []
        department_hits = result.get("departmentHits") or []
        passed = bool(department_hits) and not hard_fail_hits and not result.get("suspectedFail")
        checked.append({
            "caseId": case["caseId"],
            "title": case["title"],
            "type": case["type"],
            "source": case["source"],
            "resultFile": str(RESULTS.relative_to(ROOT)).replace("\\", "/"),
            "departmentHits": department_hits,
            "mustAskCoverage": None if must_total == 0 else round(must_hit / must_total, 4),
            "hardFailHits": hard_fail_hits,
            "suspectedFail": result.get("suspectedFail"),
            "regressionPassed": passed,
        })
    payload = {
        "generatedAt": datetime.now().isoformat(timespec="seconds"),
        "sampleCount": len(checked),
        "passedCount": sum(1 for item in checked if item["regressionPassed"]),
        "items": checked,
    }
    (OUT_DIR / "bad_case_regression_results.json").write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    lines = [
        "# Bad Case 回归结果",
        "",
        f"- generated_at: {payload['generatedAt']}",
        f"- 样本数: {payload['sampleCount']}",
        f"- 通过数: {payload['passedCount']}",
        "",
        "| 案例 | 类型 | 是否通过 | Must-Ask 覆盖率 | 科室命中 | Hard Fail 命中 |",
        "|---|---|---:|---:|---|---|",
    ]
    for item in checked:
        lines.append(
            f"| {item['caseId']} | {item['type']} | {item['regressionPassed']} | "
            f"{item['mustAskCoverage']} | {item['departmentHits']} | {item['hardFailHits']} |"
        )
    (OUT_DIR / "bad_case_regression_results.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Wrote {OUT_DIR / 'bad_case_regression_results.json'}")
    print(f"Wrote {OUT_DIR / 'bad_case_regression_results.md'}")


if __name__ == "__main__":
    main()
