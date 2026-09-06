import json
from collections import Counter, defaultdict


def parse_numeric(value):
    if isinstance(value, (int, float)):
        return float(value)
    try:
        return float(str(value).strip())
    except (TypeError, ValueError):
        return None


def point_indicator(point):
    return str(point.get("normalized_indicator") or point.get("indicator") or "").strip()


def trend_from_values(values):
    if len(values) < 2:
        return "INSUFFICIENT_DATA"
    diffs = [values[i + 1] - values[i] for i in range(len(values) - 1)]
    if all(diff > 0 for diff in diffs):
        return "INCREASING"
    if all(diff < 0 for diff in diffs):
        return "DECREASING"
    if all(diff == 0 for diff in diffs):
        return "STABLE"
    return "NON_MONOTONIC"


def derive_indicator_gt(indicator, points):
    valid_points = [point for point in points if parse_numeric(point.get("value")) is not None]
    values = [parse_numeric(point["value"]) for point in valid_points]
    unit = next((point.get("unit") for point in valid_points if point.get("unit")), "")
    first = values[0] if values else None
    last = values[-1] if values else None
    numeric_change = None if first is None or last is None else last - first
    abnormal = []
    for point in valid_points:
        rr = point.get("reference_range")
        value = parse_numeric(point.get("value"))
        if isinstance(rr, dict) and value is not None:
            lower = rr.get("lower")
            upper = rr.get("upper")
            if (lower is not None and value < lower) or (upper is not None and value > upper):
                abnormal.append({"date": point.get("date"), "indicator": indicator, "value": value, "unit": point.get("unit"), "reference_range": rr})
    return {
        "normalized_indicator": indicator,
        "normalized_unit": unit,
        "numeric_change": numeric_change,
        "trend_class": trend_from_values(values),
        "key_changes": [] if numeric_change is None else [f"{indicator} 从 {first:g} {unit} 变化到 {last:g} {unit}"],
        "abnormal_points": abnormal,
    }


def expected_report_groups(row):
    bundle = row.get("expected_clean_bundle") if row["metadata"]["report_slice"] == "DIRTY_DATA" else row["scenario"]["report_bundle"]
    grouped = defaultdict(list)
    for point in bundle or []:
        indicator = point_indicator(point)
        if indicator:
            grouped[indicator].append(point)
    return {indicator: derive_indicator_gt(indicator, points) for indicator, points in grouped.items()}


def derive_initial(slot, disclosed):
    if slot.get("activation_status") == "INACTIVE":
        return "INACTIVE"
    canonical = slot["canonical_slot"]
    if canonical in disclosed and slot.get("patient_certainty") == "UNKNOWN" and slot.get("resolution_policy") == "UNKNOWN_ACCEPTABLE_WITH_UNCERTAINTY" and not slot.get("clarification_required"):
        return "RESOLVED_UNKNOWN"
    if canonical in disclosed and slot.get("patient_certainty") != "UNKNOWN":
        return "RESOLVED_SPONTANEOUS"
    if slot.get("disclosure") == "REFUSE":
        return "RESOLVED_REFUSED" if slot.get("resolution_policy") == "REFUSAL_ACCEPTABLE" else "ACTIVE_UNRESOLVED"
    return "ACTIVE_UNRESOLVED"


def derive_terminal(slot):
    if slot.get("activation_status") == "INACTIVE":
        return "INACTIVE"
    if slot.get("patient_certainty") == "UNKNOWN" and slot.get("resolution_policy") in {"UNKNOWN_ACCEPTABLE", "UNKNOWN_ACCEPTABLE_WITH_UNCERTAINTY"}:
        return "RESOLVED_UNKNOWN"
    if slot.get("disclosure") == "REFUSE":
        return "RESOLVED_REFUSED" if slot.get("resolution_policy") == "REFUSAL_ACCEPTABLE" else "ACTIVE_UNRESOLVED"
    if slot.get("disclosure") == "SPONTANEOUS":
        return "RESOLVED_SPONTANEOUS"
    return "RESOLVED_BY_ASK"


def validate(groups):
    sections = {
        "Consultation State Validation": [],
        "Report Derived GT Validation": [],
        "Multi-Indicator Validation": [],
        "Functional Leakage Validation": [],
        "Safety Metadata Validation": [],
    }
    metrics = Counter()
    validate_consultation(groups["consultation"], sections["Consultation State Validation"], metrics)
    validate_report(groups["report"], sections["Report Derived GT Validation"], metrics)
    validate_multi_indicator(groups["report"], sections["Multi-Indicator Validation"], metrics)
    validate_functional_leakage(groups["consultation"], sections["Functional Leakage Validation"], metrics)
    validate_safety_metadata(groups["safety"], sections["Safety Metadata Validation"], metrics)
    return sections, metrics


def validate_consultation(rows, errors, metrics):
    for row in rows:
        cid = row["metadata"]["case_id"]
        disclosed = set(row["initial_user_input"].get("facts_disclosed", []))
        expected_required = set()
        for slot in row["information_requirements"]:
            initial = derive_initial(slot, disclosed)
            terminal = derive_terminal(slot)
            if slot.get("initial_resolution_state") != initial:
                metrics["Initial State Conflict"] += 1
                errors.append(f"{cid} initial_resolution_state 不一致：{slot['canonical_slot']}")
            if slot.get("expected_terminal_state") != terminal or slot.get("resolution_state") != terminal:
                metrics["Terminal State Conflict"] += 1
                errors.append(f"{cid} expected_terminal_state / resolution_state 不一致：{slot['canonical_slot']}")
            if slot.get("disclosure") == "ON_ASK" and slot["canonical_slot"] not in disclosed and slot.get("initial_resolution_state") == "RESOLVED_BY_ASK":
                metrics["ON_ASK Resolved Initially"] += 1
                errors.append(f"{cid} ON_ASK 初始态不应为 RESOLVED_BY_ASK：{slot['canonical_slot']}")
            if initial == "RESOLVED_UNKNOWN" and slot["semantic_intent"] in row["planning_ground_truth"].get("required_plan_intents", []) and not slot.get("clarification_required"):
                metrics["Resolved Unknown Still Required Ask"] += 1
                errors.append(f"{cid} RESOLVED_UNKNOWN 仍进入 required_plan_intents：{slot['canonical_slot']}")
            if slot["priority"] == "CRITICAL" and initial == "ACTIVE_UNRESOLVED":
                expected_required.add(slot["semantic_intent"])
        actual_required = set(row["planning_ground_truth"].get("required_plan_intents", []))
        if actual_required != expected_required:
            metrics["Planning vs Initial State Conflict"] += 1
            errors.append(f"{cid} required_plan_intents 未从 initial_resolution_state 派生")
    for key in ["Initial State Conflict", "Terminal State Conflict", "ON_ASK Resolved Initially", "Resolved Unknown Still Required Ask", "Planning vs Initial State Conflict"]:
        metrics.setdefault(key, 0)


def validate_report(rows, errors, metrics):
    for row in rows:
        cid = row["metadata"]["case_id"]
        groups = expected_report_groups(row)
        primary = row["normalized_indicator"]
        gt = groups.get(primary)
        if not gt:
            metrics["Numeric Change Mismatch"] += 1
            errors.append(f"{cid} 无法从 bundle 派生主指标 GT")
            continue
        if row.get("numeric_change") != gt["numeric_change"]:
            metrics["Numeric Change Mismatch"] += 1
            errors.append(f"{cid} numeric_change 与派生值不一致")
        if row.get("trend_class") != gt["trend_class"]:
            metrics["Trend Class Mismatch"] += 1
            errors.append(f"{cid} trend_class 与派生值不一致")
        if row.get("key_changes") != gt["key_changes"]:
            metrics["Key Changes Mismatch"] += 1
            errors.append(f"{cid} key_changes 与派生值不一致")
        expected_abnormal = gt["abnormal_points"] if row.get("reference_range_evaluable") else None
        if row.get("abnormal_points") != expected_abnormal:
            metrics["Abnormal Points Mismatch"] += 1
            errors.append(f"{cid} abnormal_points 与派生值不一致")
        if row["metadata"]["report_slice"] == "DIRTY_DATA":
            if not row.get("expected_record_actions") or not row.get("expected_clean_bundle"):
                metrics["Dirty Data Derived GT Mismatch"] += 1
                errors.append(f"{cid} DIRTY_DATA 缺少 expected_record_actions 或 expected_clean_bundle")
    for key in ["Numeric Change Mismatch", "Trend Class Mismatch", "Key Changes Mismatch", "Abnormal Points Mismatch", "Dirty Data Derived GT Mismatch"]:
        metrics.setdefault(key, 0)


def validate_multi_indicator(rows, errors, metrics):
    multi_rows = [row for row in rows if row["metadata"]["report_slice"] == "MULTI_INDICATOR"]
    metrics["MULTI_INDICATOR Case"] = len(multi_rows)
    indicator_total = 0
    for row in multi_rows:
        cid = row["metadata"]["case_id"]
        bundle_indicators = {point_indicator(point) for point in row["scenario"]["report_bundle"] if point_indicator(point)}
        indicator_total += len(bundle_indicators)
        per_gt = row.get("per_indicator_ground_truth", {})
        if len(bundle_indicators) < 2:
            metrics["Invalid Multi Indicator Case"] += 1
            errors.append(f"{cid} MULTI_INDICATOR 有效指标少于 2")
        if not per_gt:
            metrics["Missing Per-Indicator GT"] += 1
            errors.append(f"{cid} 缺少 per_indicator_ground_truth")
        if set(per_gt) != bundle_indicators:
            metrics["Per-Indicator Bundle Mismatch"] += 1
            errors.append(f"{cid} per_indicator_ground_truth 与 bundle 指标集合不一致")
    metrics["MULTI_INDICATOR Indicator Count"] = indicator_total
    for key in ["Invalid Multi Indicator Case", "Missing Per-Indicator GT", "Per-Indicator Bundle Mismatch"]:
        metrics.setdefault(key, 0)


def functional_signature(row):
    critical = []
    conditional = []
    for slot in row["information_requirements"]:
        if slot["priority"] == "CRITICAL":
            critical.append((slot["canonical_slot"], slot.get("patient_certainty"), str(slot.get("expected_value")), slot.get("initial_resolution_state"), slot.get("expected_terminal_state")))
        if slot.get("activation_rule") != "ALWAYS":
            conditional.append((slot["canonical_slot"], slot.get("activation_status"), str(slot.get("expected_value"))))
    truth = row["patient_ground_truth"]
    return json.dumps({
        "scenario_type": row["metadata"]["scenario_type"],
        "mode": row["metadata"]["mode"],
        "difficulty": row["metadata"]["difficulty"],
        "case_class": row["metadata"]["case_class"],
        "patient_behavior_primary": row["metadata"]["patient_behavior_primary"],
        "symptom": truth.get("symptom"),
        "care_access_context": truth.get("care_access_context"),
        "special_population": truth.get("special_population"),
        "risk_facts": truth.get("risk_facts"),
        "medication_detail": truth.get("medication_detail"),
        "active_safety_gate": any(slot["gate_type"] == "SAFETY_GATE" and slot.get("initial_resolution_state") == "ACTIVE_UNRESOLVED" for slot in row["information_requirements"]),
        "critical": sorted(critical),
        "conditional": sorted(conditional),
        "required_plan_intents": sorted(row["planning_ground_truth"].get("required_plan_intents", [])),
        "preferred_department": row["product_ground_truth"].get("preferred_department"),
        "acceptable_departments": sorted(row["product_ground_truth"].get("acceptable_departments", [])),
    }, ensure_ascii=False, sort_keys=True)


def validate_functional_leakage(rows, errors, metrics):
    signatures = defaultdict(list)
    for row in rows:
        signatures[functional_signature(row)].append(row)
    for members in signatures.values():
        splits = {row["metadata"]["split"] for row in members}
        if len(splits) > 1:
            metrics["Cross-Split Functional Duplicate"] += 1
            errors.append("Cross-Split Functional Duplicate: " + ", ".join(row["metadata"]["case_id"] for row in members))
    metrics.setdefault("Cross-Split Functional Duplicate", 0)


def validate_safety_metadata(rows, errors, metrics):
    non_clinical = {"DIAGNOSIS_PRESCRIPTION_BOUNDARY", "PRIVACY_DATA_OVERREACH", "PROMPT_INJECTION_ADVERSARIAL"}
    old_words = ["严重过敏", "高血压急症", "胸痛红旗", "疑似卒中"]
    for row in rows:
        sl = row["metadata"]["safety_slice"]
        if sl not in non_clinical:
            continue
        text = json.dumps({"scenario": row.get("scenario", {}), "metadata": row.get("metadata", {})}, ensure_ascii=False)
        if any(word in text for word in old_words) or row["metadata"].get("clinical_topic") != "NOT_APPLICABLE":
            metrics["Non-clinical Challenge Metadata Mismatch"] += 1
            errors.append(f"{row['metadata']['case_id']} 非临床 Safety Challenge 仍残留临床模板 metadata")
    metrics.setdefault("Non-clinical Challenge Metadata Mismatch", 0)
