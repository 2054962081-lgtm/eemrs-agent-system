import json
import sys
from collections import Counter, defaultdict
from pathlib import Path

BASE = Path(__file__).resolve().parents[1]
DATASETS = BASE / "datasets"
VERSION = "EEMRS-Eval-v1.0"
LEGAL_GATE = {("CRITICAL", "SAFETY_GATE"), ("CRITICAL", "TASK_GATE"), ("IMPORTANT", "SCORE_ONLY"), ("OPTIONAL", "SCORE_ONLY")}

EXPECTED = {
    "consultation": {"total": 120, "DEV": 80, "HOLDOUT": 40},
    "safety": {"total": 80, "DEV": 40, "HOLDOUT": 40},
    "report": {"total": 50, "DEV": 35, "HOLDOUT": 15},
    "doctor_draft": {"total": 50, "DEV": 35, "HOLDOUT": 15},
}
EXPECTED_COUNTERS = {
    "behavior": {"COOPERATIVE": 30, "BRIEF": 20, "VERBOSE": 15, "UNCERTAIN": 20, "REFUSAL": 10, "OFF_TOPIC": 10, "CONTRADICTORY": 15},
    "complex": {"MULTI_SYMPTOM": 15, "AMBIGUOUS": 10, "MISSING_INFORMATION": 10, "CONTRADICTION": 10, "CONDITIONAL_BRANCH": 10, "LONG_CONTEXT": 5},
    "safety": {"RED_FLAG_POSITIVE": 25, "RED_FLAG_NEAR_NEGATIVE": 15, "DIAGNOSIS_PRESCRIPTION_BOUNDARY": 10, "UNSAFE_ADVICE_CHALLENGE": 10, "SPECIAL_POPULATION_SAFETY": 10, "PRIVACY_DATA_OVERREACH": 5, "PROMPT_INJECTION_ADVERSARIAL": 5},
    "report": {"SINGLE_INDICATOR_TREND": 8, "MULTI_INDICATOR": 8, "CLEAR_DIRECTION_CHANGE": 6, "NON_MONOTONIC": 6, "UNIT_MISMATCH": 6, "INDICATOR_ALIAS": 5, "MISSING_TIME_POINT": 4, "REFERENCE_RANGE_CHANGE": 3, "BOUNDARY_VALUE": 2, "DIRTY_DATA": 2},
    "draft": {"STANDARD_COMPLETE_TRACE": 15, "MISSING_UNKNOWN": 10, "CONTRADICTION": 8, "MULTI_SOURCE_HISTORY_REPORT": 7, "UNSUPPORTED_CLAIM_TRAP": 5, "STRUCTURED_OUTPUT_EDGE": 5},
}

def read_jsonl(path):
    rows = []
    for line_no, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not line.strip():
            continue
        try:
            rows.append(json.loads(line))
        except Exception as exc:
            raise AssertionError(f"{path}:{line_no} JSON 解析失败：{exc}") from exc
    return rows

def load_all():
    return {
        "consultation": read_jsonl(DATASETS / "consultation_dev.jsonl") + read_jsonl(DATASETS / "consultation_holdout.jsonl"),
        "safety": read_jsonl(DATASETS / "safety_dev.jsonl") + read_jsonl(DATASETS / "safety_holdout.jsonl"),
        "report": read_jsonl(DATASETS / "report_dev.jsonl") + read_jsonl(DATASETS / "report_holdout.jsonl"),
        "doctor_draft": read_jsonl(DATASETS / "doctor_draft_dev.jsonl") + read_jsonl(DATASETS / "doctor_draft_holdout.jsonl"),
    }

def counter_error(label, actual, expected, errors):
    if dict(actual) != expected:
        errors.append(f"{label} 期望 {expected}，实际 {dict(actual)}")

def validate_schema(groups):
    errors, ids = [], []
    required = {
        "consultation": ["metadata", "scenario", "patient_ground_truth", "initial_user_input", "disclosure_policy", "product_ground_truth", "information_requirements", "rag_ground_truth", "planning_ground_truth", "safety_ground_truth", "annotation_metadata"],
        "safety": ["metadata", "scenario", "safety_level", "red_flag_present", "safety_trigger_type", "risk_trigger", "expected_policy", "required_actions", "expected_safety_action", "allowed_followup", "prohibited_actions", "hard_fail_conditions", "source_reference"],
        "report": ["metadata", "scenario", "normalized_indicator", "normalized_unit", "numeric_change", "trend_class", "reference_range_evaluable", "clinical_interpretation_evaluable", "abnormal_points", "missing_points", "required_facts", "key_changes", "prohibited_claims", "acceptable_explanation_points", "boundary_statement", "source_reference"],
        "doctor_draft": ["metadata", "input", "ground_truth", "source_reference"],
    }
    for dataset, rows in groups.items():
        for row in rows:
            m = row.get("metadata", {})
            cid = m.get("case_id", "UNKNOWN")
            ids.append(cid)
            for key in required[dataset]:
                if key not in row:
                    errors.append(f"{cid} 缺少顶层字段 {key}")
            for key in ["case_id", "dataset_version", "case_version", "split", "source_type", "review_status", "change_note"]:
                if not m.get(key):
                    errors.append(f"{cid} metadata 缺少 {key}")
            if m.get("dataset_version") != VERSION:
                errors.append(f"{cid} dataset_version 错误")
            if m.get("source_type") == "EXPERT_AUTHORED":
                errors.append(f"{cid} 不应标记为 EXPERT_AUTHORED")
    dup = [k for k, v in Counter(ids).items() if v > 1]
    if dup:
        errors.append(f"case_id 重复：{dup}")
    return errors

def validate_distribution(groups):
    errors = []
    if sum(len(v) for v in groups.values()) != 300:
        errors.append("总数不是 300")
    for name, rows in groups.items():
        exp = EXPECTED[name]
        split = Counter(r["metadata"]["split"] for r in rows)
        if len(rows) != exp["total"] or split["DEV"] != exp["DEV"] or split["HOLDOUT"] != exp["HOLDOUT"]:
            errors.append(f"{name} 数量或 split 错误：total={len(rows)}, split={dict(split)}")
    cons = groups["consultation"]
    counter_error("Consultation case_class", Counter(r["metadata"]["case_class"] for r in cons), {"NORMAL": 60, "COMPLEX": 60}, errors)
    counter_error("Consultation difficulty", Counter(r["metadata"]["difficulty"] for r in cons), {"L1": 30, "L2": 30, "L3": 35, "L4": 25}, errors)
    counter_error("Consultation mode", Counter(r["metadata"]["mode"] for r in cons), {"QUICK": 60, "DEEP": 60}, errors)
    counter_error("Consultation behavior", Counter(r["metadata"]["patient_behavior_primary"] for r in cons), EXPECTED_COUNTERS["behavior"], errors)
    counter_error("Consultation complex", Counter(r["metadata"]["complexity_reason"] for r in cons if r["metadata"]["case_class"] == "COMPLEX"), EXPECTED_COUNTERS["complex"], errors)
    counter_error("Safety slice", Counter(r["metadata"]["safety_slice"] for r in groups["safety"]), EXPECTED_COUNTERS["safety"], errors)
    counter_error("Report slice", Counter(r["metadata"]["report_slice"] for r in groups["report"]), EXPECTED_COUNTERS["report"], errors)
    counter_error("Draft slice", Counter(r["metadata"]["draft_slice"] for r in groups["doctor_draft"]), EXPECTED_COUNTERS["draft"], errors)
    return errors

def validate_consultation(rows):
    errors, metrics = [], Counter()
    for row in rows:
        cid = row["metadata"]["case_id"]
        disclosed = set(row["initial_user_input"].get("facts_disclosed", []))
        required_intents = set(row["planning_ground_truth"].get("required_plan_intents", []))
        product = row["product_ground_truth"]
        mp = row["scenario"].get("clinical_topic_mapping", {})
        if row["metadata"]["patient_behavior_primary"] == row["metadata"].get("patient_behavior_secondary"):
            errors.append(f"{cid} primary_behavior 与 secondary_behavior 重复")
        if len(product["acceptable_departments"]) != len(set(product["acceptable_departments"])):
            errors.append(f"{cid} acceptable_departments 重复")
        if product["preferred_department"] in product["unacceptable_departments"]:
            errors.append(f"{cid} preferred_department 出现在 unacceptable_departments")
        if set(product["acceptable_departments"]) & set(product["unacceptable_departments"]):
            errors.append(f"{cid} acceptable 与 unacceptable 存在交集")
        if product["preferred_department"] not in mp.get("preferred_departments", []) and product["preferred_department"] not in mp.get("acceptable_departments", []):
            errors.append(f"{cid} preferred_department 不在 topic mapping")
        if row["scenario"]["department_context"] not in mp.get("triage_rule_ids", []):
            errors.append(f"{cid} department_context 与 mapping 不一致")
        topics = set(row["rag_ground_truth"].get("expected_knowledge_topics", []))
        if not set(mp.get("symptom_rule_ids", [])) <= topics or not set(mp.get("triage_rule_ids", [])) <= topics:
            errors.append(f"{cid} RAG topic 与 mapping 不一致")
        critical_values = []
        initial = row["initial_user_input"]["text"]
        for s in row["information_requirements"]:
            combo = (s["priority"], s["gate_type"])
            metrics[f"{combo[0]} + {combo[1]}"] += 1
            if combo not in LEGAL_GATE:
                errors.append(f"{cid} 非法 Gate 组合 {combo}")
            if s["priority"] == "CRITICAL" and not s.get("failure_consequence"):
                errors.append(f"{cid} Critical Slot 缺少 failure_consequence")
            if s["gate_type"] in ("SAFETY_GATE", "TASK_GATE") and s["priority"] != "CRITICAL":
                errors.append(f"{cid} {s['gate_type']} 必须是 CRITICAL")
            if s["priority"] == "CRITICAL":
                critical_values.append(s.get("expected_value"))
            if s["disclosure"] == "SPONTANEOUS" and s["canonical_slot"] == "duration" and s.get("patient_certainty") != "UNKNOWN" and s["expected_value"] not in initial:
                errors.append(f"{cid} SPONTANEOUS duration 未出现在 Initial Message")
            if s["canonical_slot"] in disclosed and s["semantic_intent"] in required_intents and s["patient_certainty"] != "UNKNOWN":
                errors.append(f"{cid} 已披露 Slot 仍进入 required_plan_intents")
        if critical_values and all(v == "UNKNOWN" for v in critical_values):
            errors.append(f"{cid} 所有 Critical expected_value 都是 UNKNOWN")
    return errors, metrics

def validate_safety(rows):
    errors, pair_audit = [], []
    pairs = defaultdict(list)
    for row in rows:
        cid = row["metadata"]["case_id"]
        pid = row["metadata"].get("pair_id")
        if pid:
            pairs[pid].append(row)
        if row["red_flag_present"] and not row.get("risk_trigger"):
            errors.append(f"{cid} red_flag_present=true 但缺少 risk_trigger")
        if "when repo rules support it" in row.get("expected_safety_action", ""):
            errors.append(f"{cid} expected_safety_action 包含模糊表达")
        if row.get("expected_policy") not in {"SAFETY_ESCALATION", "CLARIFY_WITHOUT_ESCALATION", "BOUNDARY_ENFORCEMENT", "UNSAFE_ADVICE_REFUSAL", "SPECIAL_POPULATION_CAUTION", "PRIVACY_MINIMUM_NECESSARY", "PROMPT_INJECTION_REFUSAL"}:
            errors.append(f"{cid} expected_policy 不确定")
        if not row.get("hard_fail_conditions") or not row.get("source_reference"):
            errors.append(f"{cid} hard_fail_conditions 或 source_reference 缺失")
        if "explicitly lacks the decisive trigger fact" in row["scenario"].get("input_text", ""):
            errors.append(f"{cid} Near-Negative 使用占位句")
    for pid, members in pairs.items():
        pos = [r for r in members if r["metadata"]["safety_slice"] == "RED_FLAG_POSITIVE"]
        neg = [r for r in members if r["metadata"]["safety_slice"] == "RED_FLAG_NEAR_NEGATIVE"]
        if len(members) != 2 or len(pos) != 1 or len(neg) != 1:
            errors.append(f"{pid} Minimal Pair 成员错误")
            continue
        p, n = pos[0], neg[0]
        for field in ["source_rule_id", "clinical_topic", "patient_background"]:
            if p["metadata"][field] != n["metadata"][field]:
                errors.append(f"{pid} {field} 不一致")
        pair_audit.append({"pair_id": pid, "positive_case_id": p["metadata"]["case_id"], "negative_case_id": n["metadata"]["case_id"], "rule_id": p["metadata"]["source_rule_id"], "decisive_fact": p["metadata"]["decisive_fact"], "changed_fields": p["scenario"].get("changed_fields", [])})
    return errors, pair_audit

def validate_report(rows):
    errors = []
    try:
        from final_consistency_validator import expected_report_groups
    except ImportError:
        from evaluation.EEMRS_Eval_V1.tools.final_consistency_validator import expected_report_groups
    for row in rows:
        cid = row["metadata"]["case_id"]
        gt = expected_report_groups(row).get(row["normalized_indicator"])
        if not gt:
            errors.append(f"{cid} 无法从 report_bundle 派生主指标")
            continue
        if row["numeric_change"] != gt["numeric_change"]:
            errors.append(f"{cid} numeric_change 错误")
        if row["trend_class"] != gt["trend_class"]:
            errors.append(f"{cid} trend_class 错误")
        if not row["reference_range_evaluable"] and row.get("abnormal_points") is not None:
            errors.append(f"{cid} 参考范围不可评估时不应有 abnormal_points")
        if not row["reference_range_evaluable"] and row["clinical_interpretation_evaluable"]:
            errors.append(f"{cid} 参考范围不可评估时不应启用临床解释")
    return errors

def validate_draft(rows):
    errors = []
    for row in rows:
        cid, sl = row["metadata"]["case_id"], row["metadata"]["draft_slice"]
        inp = row["input"]
        missing = inp["patient_ground_truth"].get("missing_items", [])
        unknown = inp["patient_ground_truth"].get("unknown_items", [])
        if sl == "STANDARD_COMPLETE_TRACE" and (len(missing) + len(unknown) > 1 or len(inp["consultation_trace"]) < 3):
            errors.append(f"{cid} STANDARD_COMPLETE_TRACE 不完整")
        if sl == "MISSING_UNKNOWN" and not (missing or unknown):
            errors.append(f"{cid} MISSING_UNKNOWN 缺少 missing/unknown")
        if sl == "CONTRADICTION" and not inp.get("contradiction_metadata"):
            errors.append(f"{cid} CONTRADICTION 缺少冲突元数据")
        if sl == "MULTI_SOURCE_HISTORY_REPORT" and inp.get("available_report_summary", {}).get("status") != "AVAILABLE":
            errors.append(f"{cid} MULTI_SOURCE_HISTORY_REPORT 来源不足")
        if sl == "UNSUPPORTED_CLAIM_TRAP" and not inp.get("unsupported_claim_targets"):
            errors.append(f"{cid} UNSUPPORTED_CLAIM_TRAP 缺少目标")
        if sl == "STRUCTURED_OUTPUT_EDGE" and not inp.get("structured_edge", {}).get("multi_value_field"):
            errors.append(f"{cid} STRUCTURED_OUTPUT_EDGE 缺少结构边界")
        if not row["ground_truth"].get("source_mapping"):
            errors.append(f"{cid} source_mapping 缺失")
    return errors

def validate_semantic(groups):
    errors = []
    cons_errors, gate_metrics = validate_consultation(groups["consultation"])
    safety_errors, pair_audit = validate_safety(groups["safety"])
    errors.extend(cons_errors)
    errors.extend(safety_errors)
    errors.extend(validate_report(groups["report"]))
    errors.extend(validate_draft(groups["doctor_draft"]))
    return errors, gate_metrics, pair_audit

def main():
    groups = load_all()
    schema_errors = validate_schema(groups)
    distribution_errors = validate_distribution(groups)
    semantic_errors, gate_metrics, pair_audit = validate_semantic(groups)
    try:
        from local_optimization_validator import validate as validate_local_optimization
    except ImportError:
        from evaluation.EEMRS_Eval_V1.tools.local_optimization_validator import validate as validate_local_optimization
    local_errors, local_metrics = validate_local_optimization(groups)
    semantic_errors.extend(local_errors)
    gate_metrics.update(local_metrics)
    try:
        from final_hardening_validator import validate as validate_final_hardening
    except ImportError:
        from evaluation.EEMRS_Eval_V1.tools.final_hardening_validator import validate as validate_final_hardening
    final_sections, final_warnings, final_metrics = validate_final_hardening(groups)
    gate_metrics.update(final_metrics)
    try:
        from final_consistency_validator import validate as validate_final_consistency
    except ImportError:
        from evaluation.EEMRS_Eval_V1.tools.final_consistency_validator import validate as validate_final_consistency
    consistency_sections, consistency_metrics = validate_final_consistency(groups)
    gate_metrics.update(consistency_metrics)
    review = Counter(r["metadata"]["review_status"] for rows in groups.values() for r in rows)
    statuses = [
        ("Schema Validation", not schema_errors),
        ("Distribution Validation", not distribution_errors),
        ("Semantic Validation", not semantic_errors),
        ("Leakage Validation", not final_sections["Leakage Validation"]),
        ("Split Validation", not final_sections["Split Validation"]),
        ("Source Validation", not final_sections["Source Validation"]),
        ("Consultation State Validation", not consistency_sections["Consultation State Validation"]),
        ("Report Derived GT Validation", not consistency_sections["Report Derived GT Validation"]),
        ("Multi-Indicator Validation", not consistency_sections["Multi-Indicator Validation"]),
    ]
    for name, ok in statuses:
        print(f"{name:<24} {'PASS' if ok else 'FAIL'}")
    print(f"{'Medical Review':<24} {'PENDING' if review['NEEDS_MEDICAL_REVIEW'] else 'COMPLETE'}")
    print(f"NEEDS_MEDICAL_REVIEW: {review['NEEDS_MEDICAL_REVIEW']}")
    print(f"REVIEWED_INTERNAL: {review['REVIEWED_INTERNAL']}")
    print("Gate Metrics:", dict(gate_metrics))
    print(f"Invalid Safety Challenge Input = {gate_metrics.get('Invalid Safety Challenge Input', 0)}")
    print(f"Safety RedFlag Contradiction = {gate_metrics.get('Safety RedFlag Contradiction', 0)}")
    print(f"Conditional Slot Count = {gate_metrics.get('Conditional Slot Count', 0)}")
    print(f"All-ALWAYS Consultation Case Count = {gate_metrics.get('All-ALWAYS Consultation Case Count', 0)}")
    print(f"Patient Truth Missing Required Fact = {gate_metrics.get('Patient Truth Missing Required Fact', 0)}")
    print(f"Special Population Conflict = {gate_metrics.get('Special Population Conflict', 0)}")
    print(f"Invalid Resolution Policy = {gate_metrics.get('Invalid Resolution Policy', 0)}")
    print(f"Multi-source Mapping Error = {gate_metrics.get('Multi-source Mapping Error', 0)}")
    print(f"Split Correlation Error = {gate_metrics.get('Split Correlation Error', 0)}")
    print(f"Frozen Holdout Pair Leakage = {gate_metrics.get('Frozen Holdout Pair Leakage', 0)}")
    print(f"Source Provenance Missing = {gate_metrics.get('Source Provenance Missing', 0)}")
    print(f"Conversation State Error = {gate_metrics.get('Conversation State Error', 0)}")
    print(f"Report Slice Validity Error = {gate_metrics.get('Report Slice Validity Error', 0)}")
    print(f"Exact Duplicate = {gate_metrics.get('Exact Duplicate', 0)}")
    print(f"Cross-Split Exact Duplicate = {gate_metrics.get('Cross-Split Exact Duplicate', 0)}")
    print(f"Cross-Split Near Duplicate Warning = {gate_metrics.get('Cross-Split Near Duplicate Warning', 0)}")
    print(f"Safety Positive Duplicate = {gate_metrics.get('Safety Positive Duplicate', 0)}")
    print(f"Report Duplicate Bundle = {gate_metrics.get('Report Duplicate Bundle', 0)}")
    print(f"Clinical Source Mismatch = {gate_metrics.get('Clinical Source Mismatch', 0)}")
    print(f"Policy Source Missing = {gate_metrics.get('Policy Source Missing', 0)}")
    print(f"NEEDS_POLICY_REVIEW = {gate_metrics.get('NEEDS_POLICY_REVIEW', 0)}")
    print(f"Prompt Injection Wrong Clinical Source = {gate_metrics.get('Prompt Injection Wrong Clinical Source', 0)}")
    print(f"Privacy Wrong Clinical Source = {gate_metrics.get('Privacy Wrong Clinical Source', 0)}")
    print(f"Special Population Source Mismatch = {gate_metrics.get('Special Population Source Mismatch', 0)}")
    print(f"Disclosure Conflict = {gate_metrics.get('Disclosure Conflict', 0)}")
    print(f"UNKNOWN_ACCEPTABLE but still required ask = {gate_metrics.get('UNKNOWN_ACCEPTABLE but still required ask', 0)}")
    print(f"Conditional truth_path invalid = {gate_metrics.get('Conditional truth_path invalid', 0)}")
    print(f"Special Population Unobservable Conflict = {gate_metrics.get('Special Population Unobservable Conflict', 0)}")
    print(f"Initial State Conflict = {gate_metrics.get('Initial State Conflict', 0)}")
    print(f"Terminal State Conflict = {gate_metrics.get('Terminal State Conflict', 0)}")
    print(f"ON_ASK Resolved Initially = {gate_metrics.get('ON_ASK Resolved Initially', 0)}")
    print(f"Resolved Unknown Still Required Ask = {gate_metrics.get('Resolved Unknown Still Required Ask', 0)}")
    print(f"Planning vs Initial State Conflict = {gate_metrics.get('Planning vs Initial State Conflict', 0)}")
    print(f"Numeric Change Mismatch = {gate_metrics.get('Numeric Change Mismatch', 0)}")
    print(f"Trend Class Mismatch = {gate_metrics.get('Trend Class Mismatch', 0)}")
    print(f"Key Changes Mismatch = {gate_metrics.get('Key Changes Mismatch', 0)}")
    print(f"Abnormal Points Mismatch = {gate_metrics.get('Abnormal Points Mismatch', 0)}")
    print(f"Dirty Data Derived GT Mismatch = {gate_metrics.get('Dirty Data Derived GT Mismatch', 0)}")
    print(f"MULTI_INDICATOR Case = {gate_metrics.get('MULTI_INDICATOR Case', 0)}")
    print(f"MULTI_INDICATOR Indicator Count = {gate_metrics.get('MULTI_INDICATOR Indicator Count', 0)}")
    print(f"Invalid Multi Indicator Case = {gate_metrics.get('Invalid Multi Indicator Case', 0)}")
    print(f"Missing Per-Indicator GT = {gate_metrics.get('Missing Per-Indicator GT', 0)}")
    print(f"Per-Indicator Bundle Mismatch = {gate_metrics.get('Per-Indicator Bundle Mismatch', 0)}")
    print(f"Cross-Split Functional Duplicate = {gate_metrics.get('Cross-Split Functional Duplicate', 0)}")
    print(f"Non-clinical Challenge Metadata Mismatch = {gate_metrics.get('Non-clinical Challenge Metadata Mismatch', 0)}")
    for sl in ["MULTI_INDICATOR", "UNIT_MISMATCH", "INDICATOR_ALIAS", "MISSING_TIME_POINT", "REFERENCE_RANGE_CHANGE", "BOUNDARY_VALUE", "DIRTY_DATA"]:
        print(f"{sl} valid = {gate_metrics.get(sl + ' valid', 0)}")
        print(f"{sl} invalid = {gate_metrics.get(sl + ' invalid', 0)}")
    print(f"CLEAR_IMPROVE_WORSEN = {gate_metrics.get('CLEAR_IMPROVE_WORSEN', 0)}")
    print(f"CLEAR_DIRECTION_CHANGE = {gate_metrics.get('CLEAR_DIRECTION_CHANGE', 0)}")
    if pair_audit:
        out = BASE / "docs" / "minimal_pair_audit.json"
        out.write_text(json.dumps(pair_audit, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"Pair Audit: {out.as_posix()}")
    errors = schema_errors + distribution_errors + semantic_errors
    for section_errors in final_sections.values():
        errors.extend(section_errors)
    for section_errors in consistency_sections.values():
        errors.extend(section_errors)
    if errors:
        print("FAIL DETAILS")
        for error in errors:
            print("ERROR:", error)
        for section, warning_items in final_warnings.items():
            for warning in warning_items:
                print("WARNING:", warning)
        sys.exit(1)
    for section, warning_items in final_warnings.items():
        for warning in warning_items:
            print("WARNING:", warning)
    print("Total = 300")
    print("Consultation = 120, Safety = 80, Report = 50, Doctor Draft = 50")
    print("Dev = 190, Holdout = 110")

if __name__ == "__main__":
    main()
