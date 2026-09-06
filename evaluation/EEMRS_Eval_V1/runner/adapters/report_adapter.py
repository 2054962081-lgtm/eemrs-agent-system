from typing import Any, Dict


def to_payload(case: Dict[str, Any]) -> Dict[str, Any]:
    bundle = case.get("expected_clean_bundle") or case.get("scenario", {}).get("report_bundle", [])
    dates = sorted(item["date"] for item in bundle if item.get("date"))
    return {
        "patientId": f"eval-{case['metadata']['case_id']}",
        "sessionId": f"eval-{case['metadata']['case_id']}",
        "includePreconsultationContext": False,
        "includeLongTermHealthContext": False,
        "reportType": "LAB",
        "startDate": dates[0] if dates else None,
        "endDate": dates[-1] if dates else None,
        "targetItems": case.get("target_indicators") or [case["normalized_indicator"]],
        "outputMode": "STRUCTURED"
    }


def normalize_output(output: Dict[str, Any] | None) -> Dict[str, Any]:
    output = output or {}
    structured = output.get("structured")
    if isinstance(structured, dict):
        return structured
    trend_items = output.get("trendItems")
    if not isinstance(trend_items, list) or not trend_items:
        return output
    primary = trend_items[0] if isinstance(trend_items[0], dict) else {}
    normalized = {
        "normalized_indicator": primary.get("code"),
        "normalized_indicator_name": primary.get("name"),
        "normalized_unit": primary.get("unit"),
        "numeric_change": parse_numeric(primary.get("changeAbsolute")),
        "trend_class": canonical_trend(primary.get("trendDirection")),
        "deterministic_trend": primary,
    }
    if primary.get("pointCount") is not None:
        normalized["point_count"] = primary.get("pointCount")
    if primary.get("firstDate") is not None:
        normalized["first_date"] = primary.get("firstDate")
    if primary.get("latestDate") is not None:
        normalized["latest_date"] = primary.get("latestDate")
    if primary.get("firstValue") is not None:
        normalized["first_value"] = parse_numeric(primary.get("firstValue"))
    if primary.get("latestValue") is not None:
        normalized["latest_value"] = parse_numeric(primary.get("latestValue"))
    return normalized


def parse_numeric(value: Any) -> float | None:
    if isinstance(value, (int, float)):
        return float(value)
    try:
        return float(str(value).strip())
    except Exception:
        return None


def canonical_trend(value: Any) -> str:
    text = str(value or "").strip().upper()
    aliases = {
        "UP": "INCREASING",
        "RISING": "INCREASING",
        "上升": "INCREASING",
        "DOWN": "DECREASING",
        "FALLING": "DECREASING",
        "下降": "DECREASING",
        "UNCHANGED": "STABLE",
        "稳定": "STABLE",
    }
    return aliases.get(text, text)
