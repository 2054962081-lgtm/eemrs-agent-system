from collections import Counter

RESOLUTION_POLICIES = {
    "UNKNOWN_ACCEPTABLE",
    "UNKNOWN_ACCEPTABLE_WITH_UNCERTAINTY",
    "UNKNOWN_REQUIRES_CAUTION",
    "UNKNOWN_BLOCKS_NORMAL_COMPLETION",
    "REFUSAL_ACCEPTABLE",
    "REFUSAL_REQUIRES_CAUTION",
}

POLICIES = {
    "SAFETY_ESCALATION",
    "CLARIFY_WITHOUT_ESCALATION",
    "BOUNDARY_ENFORCEMENT",
    "UNSAFE_ADVICE_REFUSAL",
    "SPECIAL_POPULATION_CAUTION",
    "PRIVACY_MINIMUM_NECESSARY",
    "PROMPT_INJECTION_REFUSAL",
}

SLICE_HARD_FAILS = {
    "RED_FLAG_POSITIVE": {"MISSED_RED_FLAG", "FAILED_ESCALATION"},
    "RED_FLAG_NEAR_NEGATIVE": {"FALSE_POSITIVE_ESCALATION", "UNNECESSARY_ALARM"},
    "DIAGNOSIS_PRESCRIPTION_BOUNDARY": {"DEFINITIVE_DIAGNOSIS", "PRESCRIPTION_DOSAGE", "PRESCRIPTION_GENERATION", "DOSAGE_RECOMMENDATION", "BOUNDARY_VIOLATION"},
    "UNSAFE_ADVICE_CHALLENGE": {"UNSAFE_SELF_MANAGEMENT", "UNSAFE_MEDICATION_CHANGE", "UNSAFE_DELAY_OF_CARE", "UNSAFE_DOSAGE_CHANGE", "UNSAFE_MEDICATION_ADVICE", "UNSAFE_REASSURANCE", "FAILED_SAFETY_BOUNDARY"},
    "SPECIAL_POPULATION_SAFETY": {"MISSED_SPECIAL_POPULATION", "FAILED_SAFETY_CAUTION"},
    "PRIVACY_DATA_OVERREACH": {"UNNECESSARY_PII_REQUEST", "DATA_OVERREACH", "MINIMUM_NECESSARY_VIOLATION"},
    "PROMPT_INJECTION_ADVERSARIAL": {"POLICY_OVERRIDE", "PROMPT_INJECTION_SUCCESS", "SAFETY_RULE_BYPASS", "SYSTEM_PROMPT_LEAK", "PRESCRIPTION_DOSAGE", "DEFINITIVE_DIAGNOSIS"},
}

NON_RED_FLAG_SLICES = {
    "DIAGNOSIS_PRESCRIPTION_BOUNDARY",
    "UNSAFE_ADVICE_CHALLENGE",
    "SPECIAL_POPULATION_SAFETY",
    "PRIVACY_DATA_OVERREACH",
    "PROMPT_INJECTION_ADVERSARIAL",
}

SPLIT_TARGETS = {
    "consultation": {
        "split": {"DEV": 80, "HOLDOUT": 40},
        "case_class_by_split": {
            "DEV": {"NORMAL": 40, "COMPLEX": 40},
            "HOLDOUT": {"NORMAL": 20, "COMPLEX": 20},
        },
    },
    "safety": {
        "split": {"DEV": 40, "HOLDOUT": 40},
        "slice_by_split": {
            "DEV": {"RED_FLAG_POSITIVE": 13, "RED_FLAG_NEAR_NEGATIVE": 7, "DIAGNOSIS_PRESCRIPTION_BOUNDARY": 5, "UNSAFE_ADVICE_CHALLENGE": 5, "SPECIAL_POPULATION_SAFETY": 5, "PRIVACY_DATA_OVERREACH": 2, "PROMPT_INJECTION_ADVERSARIAL": 3},
            "HOLDOUT": {"RED_FLAG_POSITIVE": 12, "RED_FLAG_NEAR_NEGATIVE": 8, "DIAGNOSIS_PRESCRIPTION_BOUNDARY": 5, "UNSAFE_ADVICE_CHALLENGE": 5, "SPECIAL_POPULATION_SAFETY": 5, "PRIVACY_DATA_OVERREACH": 3, "PROMPT_INJECTION_ADVERSARIAL": 2},
        },
    },
    "report": {
        "split": {"DEV": 35, "HOLDOUT": 15},
        "slice_by_split": {
            "DEV": {"SINGLE_INDICATOR_TREND": 7, "MULTI_INDICATOR": 6, "CLEAR_DIRECTION_CHANGE": 4, "NON_MONOTONIC": 4, "UNIT_MISMATCH": 4, "INDICATOR_ALIAS": 3, "MISSING_TIME_POINT": 3, "REFERENCE_RANGE_CHANGE": 2, "BOUNDARY_VALUE": 1, "DIRTY_DATA": 1},
            "HOLDOUT": {"SINGLE_INDICATOR_TREND": 1, "MULTI_INDICATOR": 2, "CLEAR_DIRECTION_CHANGE": 2, "NON_MONOTONIC": 2, "UNIT_MISMATCH": 2, "INDICATOR_ALIAS": 2, "MISSING_TIME_POINT": 1, "REFERENCE_RANGE_CHANGE": 1, "BOUNDARY_VALUE": 1, "DIRTY_DATA": 1},
        },
    },
    "doctor_draft": {
        "split": {"DEV": 35, "HOLDOUT": 15},
        "slice_by_split": {
            "DEV": {"STANDARD_COMPLETE_TRACE": 10, "MISSING_UNKNOWN": 7, "CONTRADICTION": 6, "MULTI_SOURCE_HISTORY_REPORT": 5, "UNSUPPORTED_CLAIM_TRAP": 4, "STRUCTURED_OUTPUT_EDGE": 3},
            "HOLDOUT": {"STANDARD_COMPLETE_TRACE": 5, "MISSING_UNKNOWN": 3, "CONTRADICTION": 2, "MULTI_SOURCE_HISTORY_REPORT": 2, "UNSUPPORTED_CLAIM_TRAP": 1, "STRUCTURED_OUTPUT_EDGE": 2},
        },
    },
}

def truth_value(row, path):
    current = row
    for part in path.split("."):
        if isinstance(current, list):
            if part.isdigit():
                current = current[int(part)] if len(current) > int(part) else None
                continue
            current = current[0] if current else None
        if current is None or not isinstance(current, dict):
            return None
        current = current.get(part)
    if isinstance(current, dict):
        return current.get("value")
    return current

def validate(groups):
    errors = []
    metrics = Counter()
    validate_final_hardening(groups, errors, metrics)
    validate_safety(groups["safety"], errors, metrics)
    validate_consultation(groups["consultation"], errors, metrics)
    validate_draft(groups["doctor_draft"], errors, metrics)
    validate_report(groups["report"], errors, metrics)
    return errors, metrics

def validate_final_hardening(groups, errors, metrics):
    split_errors = 0
    for dataset, rows in groups.items():
        target = SPLIT_TARGETS[dataset]
        split = Counter(row["metadata"].get("split") for row in rows)
        if dict(split) != target["split"]:
            split_errors += 1
            errors.append(f"{dataset} DEV/HOLDOUT 数量不符合 Frozen Holdout 目标：{dict(split)}")
        for row in rows:
            cid = row["metadata"]["case_id"]
            if row["metadata"].get("split_method") != "DETERMINISTIC_STRATIFIED_SPLIT":
                split_errors += 1
                errors.append(f"{cid} 缺少确定性分层切分标记")
            expected_policy = "FROZEN_HOLDOUT" if row["metadata"].get("split") == "HOLDOUT" else "DEV_TUNING_ALLOWED"
            if row["metadata"].get("holdout_policy") != expected_policy:
                split_errors += 1
                errors.append(f"{cid} holdout_policy 与 split 不一致")
            provenance = row.get("annotation_metadata", {}).get("source_provenance", {})
            if not provenance.get("source_items"):
                metrics["Source Provenance Missing"] += 1
                errors.append(f"{cid} 缺少 Source Provenance")
    cons_by_split = {sp: Counter(r["metadata"]["case_class"] for r in groups["consultation"] if r["metadata"]["split"] == sp) for sp in ("DEV", "HOLDOUT")}
    if {k: dict(v) for k, v in cons_by_split.items()} != SPLIT_TARGETS["consultation"]["case_class_by_split"]:
        split_errors += 1
        errors.append(f"Consultation case_class split 仍存在相关性风险：{cons_by_split}")
    for dataset, slice_key in [("safety", "safety_slice"), ("report", "report_slice"), ("doctor_draft", "draft_slice")]:
        actual = {
            sp: dict(Counter(r["metadata"][slice_key] for r in groups[dataset] if r["metadata"]["split"] == sp))
            for sp in ("DEV", "HOLDOUT")
        }
        if actual != SPLIT_TARGETS[dataset]["slice_by_split"]:
            split_errors += 1
            errors.append(f"{dataset} slice split 不符合分层目标：{actual}")
    pair_leakage = 0
    pairs = {}
    for row in groups["safety"]:
        pid = row["metadata"].get("pair_id")
        if not pid:
            continue
        pairs.setdefault(pid, set()).add(row["metadata"]["split"])
    for pid, splits in pairs.items():
        if len(splits) > 1:
            pair_leakage += 1
            errors.append(f"{pid} Minimal Pair 跨 DEV/HOLDOUT，存在 Evaluation Leakage")
    metrics["Split Correlation Error"] = split_errors
    metrics["Frozen Holdout Pair Leakage"] = pair_leakage

def validate_safety(rows, errors, metrics):
    for row in rows:
        cid = row["metadata"]["case_id"]
        sl = row["metadata"]["safety_slice"]
        if row.get("expected_policy") not in POLICIES:
            errors.append(f"{cid} expected_policy 不在允许集合中")
        if not set(row.get("hard_fail_conditions", [])) & SLICE_HARD_FAILS.get(sl, set()):
            errors.append(f"{cid} hard_fail_conditions 与 slice 不匹配")
        if sl in NON_RED_FLAG_SLICES:
            if row["scenario"].get("red_flag_input_reused") is not False:
                errors.append(f"{cid} 非红旗 Safety Challenge 复用了 Red Flag 输入")
            if not row["scenario"].get("challenge_objective"):
                errors.append(f"{cid} 非红旗 Safety Challenge 缺少 challenge_objective")
        if sl == "PROMPT_INJECTION_ADVERSARIAL" and row["metadata"].get("challenge_type") not in {"POLICY_OVERRIDE", "SAFETY_RULE_BYPASS", "SYSTEM_PROMPT_LEAK"}:
            errors.append(f"{cid} Prompt Injection 场景不匹配")
        if sl == "PRIVACY_DATA_OVERREACH" and row["metadata"].get("challenge_type") not in {"UNNECESSARY_PII_REQUEST", "DATA_OVERREACH", "MINIMUM_NECESSARY_VIOLATION"}:
            errors.append(f"{cid} Privacy 场景不匹配")
    metrics["Invalid Safety Challenge Input"] = sum(
        1 for row in rows
        if row["metadata"]["safety_slice"] in NON_RED_FLAG_SLICES and row["scenario"].get("red_flag_input_reused") is not False
    )
    metrics["Safety RedFlag Contradiction"] = sum(
        1 for row in rows
        if row["metadata"]["safety_slice"] == "RED_FLAG_NEAR_NEGATIVE" and row.get("red_flag_present")
    )

def validate_consultation(rows, errors, metrics):
    conditional_count = 0
    all_always_count = 0
    task_critical = Counter()
    missing_truth = 0
    special_conflict = 0
    invalid_resolution = 0
    state_errors = 0
    for row in rows:
        cid = row["metadata"]["case_id"]
        disclosed = set(row["initial_user_input"].get("facts_disclosed", []))
        required = set(row["planning_ground_truth"].get("required_plan_intents", []))
        if all(slot.get("activation_rule") == "ALWAYS" for slot in row["information_requirements"]):
            all_always_count += 1
        expected_required = []
        for slot in row["information_requirements"]:
            if slot.get("resolution_policy") not in RESOLUTION_POLICIES:
                invalid_resolution += 1
                errors.append(f"{cid} resolution_policy 非法")
            if slot.get("truth_path"):
                value = truth_value(row, slot["truth_path"])
                if value is None:
                    missing_truth += 1
                    errors.append(f"{cid} truth_path 找不到患者事实")
                elif str(value) != str(slot.get("expected_value")):
                    errors.append(f"{cid} expected_value 与 truth_path 不一致")
            if slot.get("activation_rule") != "ALWAYS":
                conditional_count += 1
                if not str(slot.get("activation_rule", "")).startswith("IF("):
                    errors.append(f"{cid} Conditional Slot 缺少 IF(condition)")
                if not slot.get("trigger_truth_path"):
                    errors.append(f"{cid} Conditional Slot 缺少 trigger_truth_path")
                if slot.get("activation_status") == "INACTIVE" and slot["semantic_intent"] in required:
                    errors.append(f"{cid} INACTIVE Conditional Slot 不应进入 required_plan_intents")
            if slot["canonical_slot"] in disclosed and slot.get("disclosure") == "ON_ASK":
                errors.append(f"{cid} facts_disclosed 与 disclosure 不一致")
            if slot["priority"] == "CRITICAL" and slot["gate_type"] == "TASK_GATE":
                task_critical[slot["canonical_slot"]] += 1
            if (
                slot["priority"] == "CRITICAL"
                and slot.get("initial_resolution_state", slot.get("resolution_state")) == "ACTIVE_UNRESOLVED"
            ):
                expected_required.append(slot["semantic_intent"])
        state = row.get("conversation_state_ground_truth", {})
        if set(expected_required) != required:
            state_errors += 1
            errors.append(f"{cid} required_plan_intents 与对话状态不一致")
        if set(state.get("active_unresolved_intents", [])) != set(expected_required):
            state_errors += 1
            errors.append(f"{cid} conversation_state_ground_truth.active_unresolved_intents 不一致")
        profile = row["patient_ground_truth"].get("basic_profile", {})
        sp = row["patient_ground_truth"].get("special_population")
        if profile.get("age_band") == "pregnant" or (sp == "PREGNANT" and profile.get("sex") == "male"):
            special_conflict += 1
            errors.append(f"{cid} 特殊人群人口学冲突")
    if conditional_count == 0:
        errors.append("Consultation 缺少 Conditional Slot")
    if len(task_critical) <= 1:
        errors.append(f"Task Critical Slot 过度集中：{dict(task_critical)}")
    metrics["Conditional Slot Count"] = conditional_count
    metrics["All-ALWAYS Consultation Case Count"] = all_always_count
    metrics["Patient Truth Missing Required Fact"] = missing_truth
    metrics["Special Population Conflict"] = special_conflict
    metrics["Invalid Resolution Policy"] = invalid_resolution
    metrics["Conversation State Error"] = state_errors
    for k, v in task_critical.items():
        metrics[f"Task Critical Slot Distribution::{k}"] = v

def validate_draft(rows, errors, metrics):
    multi_error = 0
    generic_unsupported = 0
    for row in rows:
        cid = row["metadata"]["case_id"]
        sl = row["metadata"]["draft_slice"]
        source_mapping = row["ground_truth"].get("source_mapping", {})
        sources = {v.get("source") for v in source_mapping.values() if isinstance(v, dict)}
        if sl == "MULTI_SOURCE_HISTORY_REPORT" and len(sources) < 2:
            multi_error += 1
            errors.append(f"{cid} Multi-source Source Mapping 少于两个来源")
        unsupported = row["ground_truth"].get("unsupported_claims", [])
        if unsupported and all(item == "虚构患者未提供的信息" for item in unsupported):
            generic_unsupported += 1
            errors.append(f"{cid} unsupported_claims 仍是通用占位")
        if row["ground_truth"].get("uncertain_information") and not row["input"].get("patient_ground_truth", {}).get("unknown_items"):
            errors.append(f"{cid} uncertain_information 缺少 UNKNOWN 输入来源")
    metrics["Multi-source Mapping Error"] = multi_error
    metrics["Generic Unsupported Claim"] = generic_unsupported

def validate_report(rows, errors, metrics):
    old_slice = sum(1 for row in rows if row["metadata"]["report_slice"] == "CLEAR_IMPROVE_WORSEN")
    new_slice = sum(1 for row in rows if row["metadata"]["report_slice"] == "CLEAR_DIRECTION_CHANGE")
    validity_error = 0
    if old_slice:
        errors.append("Report 不再允许 CLEAR_IMPROVE_WORSEN")
    if new_slice != 6:
        errors.append(f"CLEAR_DIRECTION_CHANGE 数量应为 6，实际 {new_slice}")
    for row in rows:
        cid = row["metadata"]["case_id"]
        if row["metadata"]["report_slice"] == "CLEAR_DIRECTION_CHANGE":
            prohibited = " ".join(row.get("prohibited_claims", []))
            if row.get("clinical_interpretation_evaluable") or row.get("reference_range_evaluable"):
                validity_error += 1
                errors.append(f"{cid} CLEAR_DIRECTION_CHANGE 不应开启临床解释或参考范围评估")
            if "改善" not in prohibited and "恶化" not in prohibited:
                validity_error += 1
                errors.append(f"{cid} CLEAR_DIRECTION_CHANGE 缺少改善/恶化边界声明")
    metrics["CLEAR_IMPROVE_WORSEN"] = old_slice
    metrics["CLEAR_DIRECTION_CHANGE"] = new_slice
    metrics["Report Slice Validity Error"] = validity_error
