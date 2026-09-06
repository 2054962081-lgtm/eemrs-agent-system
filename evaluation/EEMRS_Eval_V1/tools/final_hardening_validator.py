import copy
import hashlib
import json
import re
from collections import Counter, defaultdict


IMPORTANT_CONSULTATION_KEYS = ["case_class", "mode", "difficulty", "patient_behavior_primary"]
SAFETY_SLICES = {
    "RED_FLAG_POSITIVE",
    "RED_FLAG_NEAR_NEGATIVE",
    "DIAGNOSIS_PRESCRIPTION_BOUNDARY",
    "UNSAFE_ADVICE_CHALLENGE",
    "SPECIAL_POPULATION_SAFETY",
    "PRIVACY_DATA_OVERREACH",
    "PROMPT_INJECTION_ADVERSARIAL",
}
REPORT_SLICES = {
    "SINGLE_INDICATOR_TREND",
    "MULTI_INDICATOR",
    "CLEAR_DIRECTION_CHANGE",
    "NON_MONOTONIC",
    "UNIT_MISMATCH",
    "INDICATOR_ALIAS",
    "MISSING_TIME_POINT",
    "REFERENCE_RANGE_CHANGE",
    "BOUNDARY_VALUE",
    "DIRTY_DATA",
}
DRAFT_SLICES = {
    "STANDARD_COMPLETE_TRACE",
    "MISSING_UNKNOWN",
    "CONTRADICTION",
    "MULTI_SOURCE_HISTORY_REPORT",
    "UNSUPPORTED_CLAIM_TRAP",
    "STRUCTURED_OUTPUT_EDGE",
}


def validate(groups):
    sections = {
        "Leakage Validation": [],
        "Split Validation": [],
        "Source Validation": [],
        "Consultation State Validation": [],
        "Report Validation": [],
    }
    warnings = {
        "Leakage Validation": [],
    }
    metrics = Counter()
    validate_leakage(groups, sections["Leakage Validation"], warnings["Leakage Validation"], metrics)
    validate_split(groups, sections["Split Validation"], metrics)
    validate_source(groups, sections["Source Validation"], metrics)
    validate_consultation_state(groups["consultation"], sections["Consultation State Validation"], metrics)
    validate_report_content(groups["report"], sections["Report Validation"], metrics)
    return sections, warnings, metrics


def stable_body(row):
    body = copy.deepcopy(row)
    metadata = body.get("metadata", {})
    for key in [
        "case_id",
        "split",
        "created_at",
        "updated_at",
        "split_method",
        "split_seed",
        "holdout_policy",
        "case_version",
        "change_note",
    ]:
        metadata.pop(key, None)
    return json.dumps(body, ensure_ascii=False, sort_keys=True)


def body_hash(row):
    return hashlib.sha256(stable_body(row).encode("utf-8")).hexdigest()


def normalized_text(text):
    text = re.sub(r"\d+", "#", text)
    text = re.sub(r"\s+", "", text)
    return text.lower()


def validate_leakage(groups, errors, warnings, metrics):
    seen = defaultdict(list)
    for dataset, rows in groups.items():
        for row in rows:
            seen[(dataset, body_hash(row))].append(row)
    exact_duplicate = 0
    cross_split_exact = 0
    for (_, _), members in seen.items():
        if len(members) > 1:
            exact_duplicate += len(members) - 1
            if len({m["metadata"]["split"] for m in members}) > 1:
                cross_split_exact += 1
    if exact_duplicate:
        errors.append(f"Exact Duplicate = {exact_duplicate}")
    if cross_split_exact:
        errors.append(f"Cross-Split Exact Duplicate = {cross_split_exact}")

    near = 0
    signatures = defaultdict(list)
    for row in groups["consultation"]:
        m = row["metadata"]
        signature = (
            m["scenario_type"],
            m["case_class"],
            m["patient_behavior_primary"],
            normalized_text(row["initial_user_input"]["text"]),
        )
        signatures[signature].append(row)
    for members in signatures.values():
        if len({m["metadata"]["split"] for m in members}) > 1:
            near += 1
    if near:
        warnings.append(f"Cross-Split Near Duplicate Warning = {near}")

    safety_text = defaultdict(list)
    for row in groups["safety"]:
        if row["metadata"]["safety_slice"] == "RED_FLAG_POSITIVE":
            safety_text[normalized_text(row["scenario"]["input_text"])].append(row)
    safety_dup = sum(len(v) - 1 for v in safety_text.values() if len(v) > 1)
    if safety_dup:
        errors.append(f"Safety Positive Duplicate = {safety_dup}")

    bundle_seen = defaultdict(list)
    for row in groups["report"]:
        bundle_key = json.dumps(
            [
                {
                    "indicator": p.get("indicator"),
                    "raw_indicator_name": p.get("raw_indicator_name"),
                    "normalized_indicator": p.get("normalized_indicator"),
                    "date": p.get("date"),
                    "value": p.get("value"),
                    "unit": p.get("unit"),
                    "reference_range": p.get("reference_range"),
                }
                for p in row["scenario"]["report_bundle"]
            ],
            ensure_ascii=False,
            sort_keys=True,
        )
        bundle_seen[bundle_key].append(row)
    report_dup = sum(len(v) - 1 for v in bundle_seen.values() if len(v) > 1)
    if report_dup:
        errors.append(f"Report Duplicate Bundle = {report_dup}")

    metrics["Exact Duplicate"] = exact_duplicate
    metrics["Cross-Split Exact Duplicate"] = cross_split_exact
    metrics["Cross-Split Near Duplicate Warning"] = near
    metrics["Safety Positive Duplicate"] = safety_dup
    metrics["Report Duplicate Bundle"] = report_dup


def validate_split(groups, errors, metrics):
    for key in IMPORTANT_CONSULTATION_KEYS:
        values = {row["metadata"][key] for row in groups["consultation"]}
        for value in values:
            splits = {row["metadata"]["split"] for row in groups["consultation"] if row["metadata"][key] == value}
            if splits != {"DEV", "HOLDOUT"}:
                errors.append(f"Consultation {key}={value} 未同时覆盖 DEV/HOLDOUT")
    for dataset, key, expected in [
        ("safety", "safety_slice", SAFETY_SLICES),
        ("report", "report_slice", REPORT_SLICES),
        ("doctor_draft", "draft_slice", DRAFT_SLICES),
    ]:
        for value in expected:
            splits = {row["metadata"]["split"] for row in groups[dataset] if row["metadata"][key] == value}
            if splits != {"DEV", "HOLDOUT"}:
                errors.append(f"{dataset} {key}={value} 未同时覆盖 DEV/HOLDOUT")
    metrics["Split Coverage Error"] = len(errors)


def validate_source(groups, errors, metrics):
    clinical_mismatch = 0
    policy_missing = 0
    prompt_wrong = 0
    privacy_wrong = 0
    special_mismatch = 0
    needs_policy_review = 0
    for row in groups["safety"]:
        cid = row["metadata"]["case_id"]
        sl = row["metadata"]["safety_slice"]
        clinical = row.get("clinical_source_reference")
        policy = row.get("policy_source_reference")
        if policy and policy.get("review_status") == "NEEDS_POLICY_REVIEW":
            needs_policy_review += 1
        if sl in {"RED_FLAG_POSITIVE", "RED_FLAG_NEAR_NEGATIVE"} and not clinical:
            clinical_mismatch += 1
            errors.append(f"{cid} Red Flag 缺少 clinical_source_reference")
        if sl in {"DIAGNOSIS_PRESCRIPTION_BOUNDARY", "UNSAFE_ADVICE_CHALLENGE", "PRIVACY_DATA_OVERREACH", "PROMPT_INJECTION_ADVERSARIAL"} and not policy:
            policy_missing += 1
            errors.append(f"{cid} policy_source_reference 缺失")
        if sl == "PROMPT_INJECTION_ADVERSARIAL" and clinical and not policy:
            prompt_wrong += 1
            errors.append(f"{cid} Prompt Injection 不能仅绑定 Red Flag clinical source")
        if sl == "PRIVACY_DATA_OVERREACH" and clinical and not policy:
            privacy_wrong += 1
            errors.append(f"{cid} Privacy 不能仅绑定 Red Flag clinical source")
        if sl == "SPECIAL_POPULATION_SAFETY" and not clinical:
            special_mismatch += 1
            errors.append(f"{cid} Special Population 缺少对应 clinical source")
    metrics["Clinical Source Mismatch"] = clinical_mismatch
    metrics["Policy Source Missing"] = policy_missing
    metrics["NEEDS_POLICY_REVIEW"] = needs_policy_review
    metrics["Prompt Injection Wrong Clinical Source"] = prompt_wrong
    metrics["Privacy Wrong Clinical Source"] = privacy_wrong
    metrics["Special Population Source Mismatch"] = special_mismatch


def validate_consultation_state(rows, errors, metrics):
    disclosure_conflict = 0
    spontaneous_missing = 0
    unknown_required = 0
    conditional_truth_invalid = 0
    special_unobservable = 0
    for row in rows:
        cid = row["metadata"]["case_id"]
        initial = row["initial_user_input"]["text"]
        disclosed = set(row["initial_user_input"].get("facts_disclosed", []))
        policy = {item["fact"]: item["disclosure"] for item in row.get("disclosure_policy", [])}
        required = set(row["planning_ground_truth"].get("required_plan_intents", []))
        for slot in row["information_requirements"]:
            canonical = slot["canonical_slot"]
            if canonical in disclosed and policy.get(canonical) == "ON_ASK":
                disclosure_conflict += 1
                errors.append(f"{cid} facts_disclosed 与 ON_ASK 冲突：{canonical}")
            if slot.get("disclosure") == "SPONTANEOUS":
                value = str(slot.get("expected_value", ""))
                if canonical != "chief_complaint" and value and value not in initial and canonical not in disclosed:
                    spontaneous_missing += 1
                    errors.append(f"{cid} SPONTANEOUS 未能从 Initial Input 派生：{canonical}")
            if (
                slot.get("patient_certainty") == "UNKNOWN"
                and slot.get("resolution_policy") == "UNKNOWN_ACCEPTABLE_WITH_UNCERTAINTY"
                and slot["semantic_intent"] in required
                and not slot.get("clarification_required")
            ):
                unknown_required += 1
                errors.append(f"{cid} UNKNOWN_ACCEPTABLE_WITH_UNCERTAINTY 仍进入 required_plan_intents")
            if slot.get("canonical_slot") == "medication_detail":
                if slot.get("truth_path") != "patient_ground_truth.medication_detail":
                    conditional_truth_invalid += 1
                    errors.append(f"{cid} medication_detail truth_path 无效")
                if "medication_detail" not in row.get("patient_ground_truth", {}):
                    conditional_truth_invalid += 1
                    errors.append(f"{cid} medication_detail 缺少真实 Hidden Truth")
        special = row["patient_ground_truth"].get("special_population")
        if special and special != "NONE":
            has_slot = any(slot["canonical_slot"] == "special_population" for slot in row["information_requirements"])
            if "special_population" not in policy or not has_slot:
                special_unobservable += 1
                errors.append(f"{cid} Special Population 不可观测")
    metrics["Disclosure Conflict"] = disclosure_conflict
    metrics["SPONTANEOUS but not in Initial Input"] = spontaneous_missing
    metrics["UNKNOWN_ACCEPTABLE but still required ask"] = unknown_required
    metrics["Conditional truth_path invalid"] = conditional_truth_invalid
    metrics["Special Population Unobservable Conflict"] = special_unobservable


def validate_report_content(rows, errors, metrics):
    invalid = Counter()
    valid = Counter()
    for row in rows:
        cid = row["metadata"]["case_id"]
        sl = row["metadata"]["report_slice"]
        bundle = row["scenario"]["report_bundle"]
        indicators = {p.get("normalized_indicator", p.get("indicator", "").strip()) for p in bundle}
        raw_indicators = {p.get("raw_indicator_name", p.get("indicator", "")).strip() for p in bundle}
        units = {p.get("unit") for p in bundle}
        dates = {p.get("date") for p in bundle}
        ranges = {json.dumps(p.get("reference_range"), sort_keys=True, ensure_ascii=False) for p in bundle if p.get("reference_range")}
        ok = True
        if sl == "MULTI_INDICATOR" and len(indicators) < 2:
            ok = False
        elif sl == "UNIT_MISMATCH" and len(units) < 2:
            ok = False
        elif sl == "INDICATOR_ALIAS" and (len(raw_indicators) < 2 or len(indicators) != 1):
            ok = False
        elif sl == "MISSING_TIME_POINT" and (set(row.get("missing_points", [])) & dates):
            ok = False
        elif sl == "REFERENCE_RANGE_CHANGE" and len(ranges) < 2:
            ok = False
        elif sl == "BOUNDARY_VALUE" and not ranges:
            ok = False
        elif sl == "DIRTY_DATA" and not row.get("dirty_features"):
            ok = False
        if ok:
            valid[sl] += 1
        else:
            invalid[sl] += 1
            errors.append(f"{cid} Report slice 内容与标签不一致：{sl}")
    for sl in ["MULTI_INDICATOR", "UNIT_MISMATCH", "INDICATOR_ALIAS", "MISSING_TIME_POINT", "REFERENCE_RANGE_CHANGE", "BOUNDARY_VALUE", "DIRTY_DATA"]:
        metrics[f"{sl} valid"] = valid[sl]
        metrics[f"{sl} invalid"] = invalid[sl]
