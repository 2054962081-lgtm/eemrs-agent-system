from collections import defaultdict
from typing import Any, Dict, List

from adapters import report_adapter


def parse_numeric(value: Any) -> float | None:
    if isinstance(value, (int, float)):
        return float(value)
    try:
        return float(str(value).strip())
    except Exception:
        return None


def evaluate(case: Dict[str, Any], output: Dict[str, Any] | None = None) -> Dict[str, Any]:
    output = output or {}
    expected = {
        "numeric_change": case.get("numeric_change"),
        "trend_class": case.get("trend_class"),
        "normalized_indicator": case.get("normalized_indicator"),
        "normalized_unit": case.get("normalized_unit"),
    }
    actual = report_adapter.normalize_output(output)
    numeric_match = numeric_equal(actual.get("numeric_change"), expected["numeric_change"]) if actual else False
    metrics = {
        "indicator_matching": actual.get("normalized_indicator") == expected["normalized_indicator"] if actual else False,
        "unit_normalization": actual.get("normalized_unit") == expected["normalized_unit"] if actual else False,
        "numeric_accuracy": numeric_match,
        "trend_accuracy": report_adapter.canonical_trend(actual.get("trend_class")) == expected["trend_class"] if actual else False,
        "multi_indicator_coverage": "NOT_APPLICABLE",
        "missing_point_handling": "NOT_APPLICABLE",
        "dirty_data_processing_accuracy": "NOT_APPLICABLE",
    }
    if case["metadata"]["report_slice"] == "MULTI_INDICATOR":
        expected_indicators = set(case.get("per_indicator_ground_truth", {}))
        actual_indicators = set((actual.get("per_indicator_ground_truth") or {}).keys()) if actual else set()
        metrics["multi_indicator_coverage"] = len(expected_indicators & actual_indicators) / len(expected_indicators) if expected_indicators else 0
    if case["metadata"]["report_slice"] == "MISSING_TIME_POINT":
        metrics["missing_point_handling"] = True
    if case["metadata"]["report_slice"] == "DIRTY_DATA":
        metrics["dirty_data_processing_accuracy"] = bool(actual.get("expected_record_actions")) if actual else False
    metrics["case_status"] = "TASK_PASS" if metrics["numeric_accuracy"] and metrics["trend_accuracy"] else "TASK_FAIL"
    return metrics


def numeric_equal(actual: Any, expected: Any, tolerance: float = 1e-6) -> bool:
    left = parse_numeric(actual)
    right = parse_numeric(expected)
    if left is None or right is None:
        return False
    return abs(left - right) <= tolerance


def oracle_output(case: Dict[str, Any]) -> Dict[str, Any]:
    return {
        "normalized_indicator": case.get("normalized_indicator"),
        "normalized_unit": case.get("normalized_unit"),
        "numeric_change": case.get("numeric_change"),
        "trend_class": case.get("trend_class"),
        "per_indicator_ground_truth": case.get("per_indicator_ground_truth", {}),
        "expected_record_actions": case.get("expected_record_actions", []),
    }
