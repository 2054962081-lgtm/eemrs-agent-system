from __future__ import annotations

from dataclasses import asdict, dataclass
from typing import Any, Dict, Iterable, List, Optional, Tuple


OBSERVED = "OBSERVED"
REQUIRES_HUMAN = "REQUIRES_HUMAN"
EVENT_LOG_REQUIRED = "EVENT_LOG_REQUIRED"
PROXY = "PROXY"
NOT_OBSERVABLE = "NOT_OBSERVABLE"
NOT_IMPLEMENTED = "NOT_IMPLEMENTED"
REQUIRES_JUDGE = "REQUIRES_JUDGE"

AUTOMATIC_GROUND_TRUTH = "AUTOMATIC_GROUND_TRUTH"
HUMAN_REVIEW = "HUMAN_REVIEW"
EVENT_LOG = "EVENT_LOG"
OFFLINE_PROXY = "OFFLINE_PROXY"

CRITICAL_DRAFT_FIELDS = {
    "chief_complaint",
    "present_illness",
    "present_illness_history",
    "red_flags",
    "key_symptoms",
    "medication",
    "allergy",
    "assessment",
    "diagnosis_advice",
    "treatment_advice",
}


@dataclass
class Metric:
    name: str
    value: Any
    status: str
    sample_count: int
    source: str
    method: str
    higher_is_better: Optional[bool]
    evidence_level: str
    source_run_id: Optional[str] = None

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)


def metric(
    name: str,
    value: Any,
    status: str,
    sample_count: int,
    source: str,
    method: str,
    higher_is_better: Optional[bool],
    evidence_level: str,
    source_run_id: Optional[str] = None,
) -> Dict[str, Any]:
    return Metric(
        name=name,
        value=value,
        status=status,
        sample_count=sample_count,
        source=source,
        method=method,
        higher_is_better=higher_is_better,
        evidence_level=evidence_level,
        source_run_id=source_run_id,
    ).to_dict()


def safe_ratio(numerator: float, denominator: float) -> Tuple[Optional[float], str]:
    if denominator == 0:
        return None, NOT_OBSERVABLE
    return numerator / denominator, OBSERVED


def bool_rate(rows: Iterable[Dict[str, Any]], field: str) -> Tuple[Optional[float], int]:
    values = []
    for row in rows:
        value = row.get("evaluation", {}).get(field)
        if isinstance(value, bool):
            values.append(value)
    if not values:
        return None, 0
    return sum(1 for value in values if value) / len(values), len(values)


def numeric_average(rows: Iterable[Dict[str, Any]], field: str) -> Tuple[Optional[float], int]:
    values = []
    for row in rows:
        value = row.get("evaluation", {}).get(field)
        if isinstance(value, (int, float)) and not isinstance(value, bool):
            values.append(float(value))
    if not values:
        return None, 0
    return sum(values) / len(values), len(values)


def normalized_edit_distance(left: str, right: str) -> Optional[float]:
    left = left or ""
    right = right or ""
    denominator = max(len(left), len(right))
    if denominator == 0:
        return None
    previous = list(range(len(right) + 1))
    for i, left_char in enumerate(left, start=1):
        current = [i]
        for j, right_char in enumerate(right, start=1):
            insertion = current[j - 1] + 1
            deletion = previous[j] + 1
            substitution = previous[j - 1] + (left_char != right_char)
            current.append(min(insertion, deletion, substitution))
        previous = current
    return previous[-1] / denominator


def field_edit_ratio(original: Dict[str, Any], final: Dict[str, Any]) -> Tuple[Optional[float], str]:
    non_empty_fields = [key for key, value in original.items() if value not in (None, "")]
    if not non_empty_fields:
        return None, NOT_OBSERVABLE
    changed = sum(1 for key in non_empty_fields if original.get(key) != final.get(key))
    return changed / len(non_empty_fields), OBSERVED


def aggregate_consultation_quality(rows: List[Dict[str, Any]], summary: Dict[str, Any], source_run_id: str) -> Dict[str, Dict[str, Any]]:
    dataset_summary = summary.get("datasets", {}).get("consultation", {})
    case_count = int(dataset_summary.get("case_count") or len(rows))
    task_pass = sum(1 for row in rows if row.get("evaluation", {}).get("task_gate") == "TASK_PASS")
    preferred = sum(1 for row in rows if row.get("evaluation", {}).get("routing") == "PREFERRED")
    acceptable = sum(1 for row in rows if row.get("evaluation", {}).get("routing") in {"PREFERRED", "ACCEPTABLE"})
    turn_values = [row.get("evaluation", {}).get("turn_count") for row in rows if isinstance(row.get("evaluation", {}).get("turn_count"), (int, float))]
    cirr_values = [row.get("evaluation", {}).get("critical_information_resolution_rate") for row in rows if isinstance(row.get("evaluation", {}).get("critical_information_resolution_rate"), (int, float))]
    system = summary.get("system_health", {})
    trace_available = int(dataset_summary.get("trace_available_count") or 0)

    def auto(name: str, value: Any, sample_count: int, method: str, higher: Optional[bool] = True) -> Dict[str, Any]:
        status = OBSERVED if value is not None else NOT_OBSERVABLE
        return metric(name, value, status, sample_count, "consultation_eval_artifact", method, higher, AUTOMATIC_GROUND_TRUTH if status == OBSERVED else NOT_OBSERVABLE, source_run_id)

    return {
        "task_completion_rate": auto("任务完成率", task_pass / case_count if case_count else None, case_count, "TASK_PASS / consultation cases"),
        "critical_information_resolution_rate": auto("关键信息解决率", sum(cirr_values) / len(cirr_values) if cirr_values else None, len(cirr_values), "mean critical_information_resolution_rate"),
        "preferred_routing_rate": auto("首选科室路由率", preferred / case_count if case_count else None, case_count, "PREFERRED routing / consultation cases"),
        "acceptable_routing_rate": auto("可接受科室路由率", acceptable / case_count if case_count else None, case_count, "(PREFERRED + ACCEPTABLE) / consultation cases"),
        "safety_red_flag_recall": metric("安全红旗召回率", None, NOT_IMPLEMENTED, 0, "consultation_eval_artifact", "no stable red-flag recall denominator in current artifact", True, NOT_OBSERVABLE, source_run_id),
        "request_success_rate": auto("请求成功率", system.get("request_success_rate"), case_count, "successful requests / requests"),
        "trace_available_rate": auto("Trace 可用率", trace_available / case_count if case_count else None, case_count, "trace_available_count / consultation cases"),
        "average_turn_count": auto("平均轮次", sum(turn_values) / len(turn_values) if turn_values else None, len(turn_values), "mean assistant turns", False),
        "max_round_rate": metric("最大轮次触达率", None, NOT_IMPLEMENTED, 0, "consultation_eval_artifact", "max-round policy not exposed as stable per-case field", False, NOT_OBSERVABLE, source_run_id),
        "p50_latency": auto("P50 系统延迟 ms", system.get("p50_latency_ms"), case_count, "system_health.p50_latency_ms", False),
        "p95_latency": auto("P95 系统延迟 ms", system.get("p95_latency_ms"), case_count, "system_health.p95_latency_ms", False),
        "premature_stop_rate": metric("过早停止率", None, NOT_IMPLEMENTED, 0, "consultation_eval_artifact", "not implemented by current evaluator", False, NOT_OBSERVABLE, source_run_id),
        "completion_state_consistency": metric("完成状态一致性", None, NOT_IMPLEMENTED, 0, "consultation_eval_artifact", "not implemented by current evaluator", True, NOT_OBSERVABLE, source_run_id),
        "redundant_ask_rate": metric("冗余追问率", None, NOT_IMPLEMENTED, 0, "consultation_eval_artifact", "current evaluator returns NOT_IMPLEMENTED_V1", False, NOT_OBSERVABLE, source_run_id),
        "irrelevant_ask_rate": metric("无关追问率", None, NOT_IMPLEMENTED, 0, "consultation_eval_artifact", "current evaluator returns NOT_IMPLEMENTED_V1", False, NOT_OBSERVABLE, source_run_id),
    }


def aggregate_report_quality(rows: List[Dict[str, Any]], summary: Dict[str, Any], source_run_id: str) -> Dict[str, Dict[str, Any]]:
    dataset_summary = summary.get("datasets", {}).get("report", {})
    case_count = int(dataset_summary.get("case_count") or len(rows))

    def observed_bool(field: str, title: str, method: str) -> Dict[str, Any]:
        value, sample_count = bool_rate(rows, field)
        status = OBSERVED if sample_count else NOT_OBSERVABLE
        return metric(title, value, status, sample_count, "report_eval_artifact", method, True, AUTOMATIC_GROUND_TRUTH if status == OBSERVED else NOT_OBSERVABLE, source_run_id)

    success_value = None if case_count == 0 else int(dataset_summary.get("task_pass_count") or 0) / case_count
    return {
        "report_generation_success_rate": metric("报告生成成功率", success_value, OBSERVED if case_count else NOT_OBSERVABLE, case_count, "report_eval_artifact", "task_pass_count / report cases", True, AUTOMATIC_GROUND_TRUTH if case_count else NOT_OBSERVABLE, source_run_id),
        "indicator_matching_accuracy": observed_bool("indicator_matching", "指标匹配准确率", "indicator_matching true / observed"),
        "numeric_accuracy": observed_bool("numeric_accuracy", "数值准确率", "numeric_accuracy true / observed"),
        "unit_normalization_accuracy": observed_bool("unit_normalization", "单位归一化准确率", "unit_normalization true / observed"),
        "trend_accuracy": observed_bool("trend_accuracy", "趋势准确率", "trend_accuracy true / observed"),
        "abnormal_change_recall": metric("异常变化召回率", None, NOT_OBSERVABLE, 0, "report_eval_artifact", "current ground truth lacks stable abnormal-change denominator", True, NOT_OBSERVABLE, source_run_id),
        "abnormal_change_precision": metric("异常变化精确率", None, NOT_OBSERVABLE, 0, "report_eval_artifact", "current artifact lacks reviewed AI abnormal-change candidates", True, NOT_OBSERVABLE, source_run_id),
        "key_change_identification_accuracy": metric("关键变化识别准确率", None, NOT_OBSERVABLE, 0, "report_eval_artifact", "current ground truth lacks stable key-change labels", True, NOT_OBSERVABLE, source_run_id),
        "evidence_grounding_rate": metric("证据可追溯率", None, NOT_OBSERVABLE, 0, "report_eval_artifact", "requires claim-level evidence annotation or review", True, NOT_OBSERVABLE, source_run_id),
    }


def aggregate_doctor_draft_quality(rows: List[Dict[str, Any]], summary: Dict[str, Any], source_run_id: str) -> Dict[str, Dict[str, Any]]:
    dataset_summary = summary.get("datasets", {}).get("doctor_draft", {})
    case_count = int(dataset_summary.get("case_count") or len(rows))
    structured_value, structured_count = bool_rate(rows, "structured_output_success")
    required_value, required_count = numeric_average(rows, "required_field_completeness")
    source_value, source_count = numeric_average(rows, "source_mapping_coverage")
    success_value = None if case_count == 0 else int(dataset_summary.get("task_pass_count") or 0) / case_count

    def status_for(count: int) -> str:
        return OBSERVED if count else NOT_OBSERVABLE

    return {
        "draft_generation_success_rate": metric("草稿生成成功率", success_value, OBSERVED if case_count else NOT_OBSERVABLE, case_count, "doctor_draft_eval_artifact", "task_pass_count / doctor_draft cases", True, AUTOMATIC_GROUND_TRUTH if case_count else NOT_OBSERVABLE, source_run_id),
        "structured_output_success_rate": metric("结构化输出成功率", structured_value, status_for(structured_count), structured_count, "doctor_draft_eval_artifact", "structured_output_success true / observed", True, AUTOMATIC_GROUND_TRUTH if structured_count else NOT_OBSERVABLE, source_run_id),
        "required_field_completeness": metric("必填字段完整度", required_value, status_for(required_count), required_count, "doctor_draft_eval_artifact", "mean required_field_completeness", True, AUTOMATIC_GROUND_TRUTH if required_count else NOT_OBSERVABLE, source_run_id),
        "source_mapping_coverage": metric("来源映射覆盖率", source_value, status_for(source_count), source_count, "doctor_draft_eval_artifact", "mean source_mapping_coverage", True, AUTOMATIC_GROUND_TRUTH if source_count else NOT_OBSERVABLE, source_run_id),
        "unsupported_claim_rate": metric("无来源支持事实率", None, REQUIRES_JUDGE, 0, "doctor_draft_eval_artifact", "requires field-level factual support judging", False, NOT_OBSERVABLE, source_run_id),
        "critical_field_accuracy": metric("关键字段准确率", None, REQUIRES_JUDGE, 0, "doctor_draft_eval_artifact", "requires reliable critical-field ground truth or judge", True, NOT_OBSERVABLE, source_run_id),
        "field_level_accuracy": metric("字段级准确率", None, REQUIRES_JUDGE, 0, "doctor_draft_eval_artifact", "requires field-level gold/reference comparison", True, NOT_OBSERVABLE, source_run_id),
    }


def aggregate_doctor_draft_reviews(reviews: List[Dict[str, Any]]) -> Dict[str, Dict[str, Any]]:
    total = len(reviews)
    if total == 0:
        return {
            "draft_acceptance_rate": metric("草稿采纳率", None, REQUIRES_HUMAN, 0, "doctor_draft_review", "accepted / reviewed", True, HUMAN_REVIEW),
            "direct_acceptance_rate": metric("直接采纳率", None, REQUIRES_HUMAN, 0, "doctor_draft_review", "directly_accepted / reviewed", True, HUMAN_REVIEW),
            "character_edit_ratio": metric("字符平均修改量", None, REQUIRES_HUMAN, 0, "doctor_draft_review", "normalized edit distance(original_draft, final_draft)", False, HUMAN_REVIEW),
            "field_edit_ratio": metric("字段平均修改量", None, REQUIRES_HUMAN, 0, "doctor_draft_review", "changed fields / original non-empty fields", False, HUMAN_REVIEW),
            "critical_field_edit_rate": metric("关键字段修改率", None, REQUIRES_HUMAN, 0, "doctor_draft_review", "changed critical fields / critical fields", False, HUMAN_REVIEW),
            "doctor_correction_rate": metric("医生事实纠正率", None, REQUIRES_HUMAN, 0, "doctor_draft_review", "FACTUAL_ERROR corrections / AI fact fields", False, HUMAN_REVIEW),
            "draft_acceptance_proxy": metric("草稿采纳率 Proxy", None, REQUIRES_HUMAN, 0, "offline_reference", "requires existing reference final record; not real clinician adoption", True, OFFLINE_PROXY),
            "draft_edit_ratio_proxy": metric("草稿修改量 Proxy", None, REQUIRES_HUMAN, 0, "offline_reference", "normalized edit distance to existing reference final record; not real clinician adoption", False, OFFLINE_PROXY),
        }

    character_values = [normalized_edit_distance(str(r.get("original_draft", "")), str(r.get("final_draft", ""))) for r in reviews if "original_draft" in r and "final_draft" in r]
    character_values = [value for value in character_values if value is not None]
    field_values = []
    critical_changed = 0
    critical_total = 0
    factual_errors = 0
    fact_fields = 0
    for review in reviews:
        has_structured_fields = isinstance(review.get("original_fields"), dict) and isinstance(review.get("final_fields"), dict)
        if has_structured_fields:
            ratio, status = field_edit_ratio(review["original_fields"], review["final_fields"])
            if status == OBSERVED and ratio is not None:
                field_values.append(ratio)
        changes = review.get("field_changes") or []
        if changes and not has_structured_fields:
            changed = sum(1 for item in changes if item.get("changed"))
            field_values.append(changed / len(changes))
        for item in changes:
            field = str(item.get("field", ""))
            if field in CRITICAL_DRAFT_FIELDS:
                critical_total += 1
                if item.get("changed"):
                    critical_changed += 1
            if item.get("reason") == "FACTUAL_ERROR":
                factual_errors += 1
        fact_fields += int(review.get("ai_fact_field_count") or len(changes))

    return {
        "draft_acceptance_rate": metric("草稿采纳率", sum(1 for r in reviews if r.get("accepted")) / total, OBSERVED, total, "doctor_draft_review", "accepted / reviewed", True, HUMAN_REVIEW),
        "direct_acceptance_rate": metric("直接采纳率", sum(1 for r in reviews if r.get("directly_accepted")) / total, OBSERVED, total, "doctor_draft_review", "directly_accepted / reviewed", True, HUMAN_REVIEW),
        "character_edit_ratio": metric("字符平均修改量", sum(character_values) / len(character_values) if character_values else None, OBSERVED if character_values else REQUIRES_HUMAN, len(character_values), "doctor_draft_review", "mean normalized edit distance(original_draft, final_draft)", False, HUMAN_REVIEW),
        "field_edit_ratio": metric("字段平均修改量", sum(field_values) / len(field_values) if field_values else None, OBSERVED if field_values else REQUIRES_HUMAN, len(field_values), "doctor_draft_review", "mean changed fields / original non-empty fields", False, HUMAN_REVIEW),
        "critical_field_edit_rate": metric("关键字段修改率", critical_changed / critical_total if critical_total else None, OBSERVED if critical_total else REQUIRES_HUMAN, critical_total, "doctor_draft_review", "changed critical fields / reviewed critical fields", False, HUMAN_REVIEW),
        "doctor_correction_rate": metric("医生事实纠正率", factual_errors / fact_fields if fact_fields else None, OBSERVED if fact_fields else REQUIRES_HUMAN, fact_fields, "doctor_draft_review", "FACTUAL_ERROR corrections / AI fact fields", False, HUMAN_REVIEW),
        "draft_acceptance_proxy": metric("草稿采纳率 Proxy", None, REQUIRES_HUMAN, 0, "offline_reference", "requires existing reference final record; not real clinician adoption", True, OFFLINE_PROXY),
        "draft_edit_ratio_proxy": metric("草稿修改量 Proxy", None, REQUIRES_HUMAN, 0, "offline_reference", "normalized edit distance to existing reference final record; not real clinician adoption", False, OFFLINE_PROXY),
    }


def aggregate_report_reviews(reviews: List[Dict[str, Any]]) -> Dict[str, Dict[str, Any]]:
    total = len(reviews)
    if total == 0:
        return {
            "report_summary_acceptance_rate": metric("报告总结采纳率", None, REQUIRES_HUMAN, 0, "report_review", "(ACCEPT + ACCEPT_WITH_MINOR_EDIT) / reviewed", True, HUMAN_REVIEW),
            "key_finding_acceptance_rate": metric("关键发现采纳率", None, REQUIRES_HUMAN, 0, "report_review", "USEFUL key findings / judged key findings", True, HUMAN_REVIEW),
            "report_correction_rate": metric("报告事实纠正率", None, REQUIRES_HUMAN, 0, "report_review", "WRONG key findings / judged key findings", False, HUMAN_REVIEW),
            "report_acceptance_proxy": metric("报告采纳率 Proxy", None, REQUIRES_HUMAN, 0, "offline_reference", "requires existing gold summary/key findings; not real clinician adoption", True, OFFLINE_PROXY),
        }
    accepted = sum(1 for r in reviews if r.get("summary_acceptance") in {"ACCEPT", "ACCEPT_WITH_MINOR_EDIT"})
    findings = [item for review in reviews for item in (review.get("key_findings") or [])]
    useful = sum(1 for item in findings if item.get("judgment") == "USEFUL")
    wrong = sum(1 for item in findings if item.get("judgment") == "WRONG")
    return {
        "report_summary_acceptance_rate": metric("报告总结采纳率", accepted / total, OBSERVED, total, "report_review", "(ACCEPT + ACCEPT_WITH_MINOR_EDIT) / reviewed", True, HUMAN_REVIEW),
        "key_finding_acceptance_rate": metric("关键发现采纳率", useful / len(findings) if findings else None, OBSERVED if findings else REQUIRES_HUMAN, len(findings), "report_review", "USEFUL key findings / judged key findings", True, HUMAN_REVIEW),
        "report_correction_rate": metric("报告事实纠正率", wrong / len(findings) if findings else None, OBSERVED if findings else REQUIRES_HUMAN, len(findings), "report_review", "WRONG key findings / judged key findings", False, HUMAN_REVIEW),
        "report_acceptance_proxy": metric("报告采纳率 Proxy", None, REQUIRES_HUMAN, 0, "offline_reference", "requires existing gold summary/key findings; not real clinician adoption", True, OFFLINE_PROXY),
    }


def paired_timing_metrics(records: List[Dict[str, Any]], task_type: str) -> Dict[str, Dict[str, Any]]:
    pairs: Dict[Tuple[str, str], Dict[str, float]] = {}
    for record in records:
        if record.get("event_type") != "TASK_TIMER" or record.get("task_type") != task_type:
            continue
        condition = record.get("condition")
        if condition not in {"baseline", "ai_assisted"}:
            continue
        duration = record.get("duration_ms")
        if not isinstance(duration, (int, float)):
            continue
        key = (str(record.get("case_id")), str(record.get("reviewer_id", "anonymous")))
        pairs.setdefault(key, {})[condition] = float(duration)
    matched = [pair for pair in pairs.values() if "baseline" in pair and "ai_assisted" in pair]
    if not matched:
        return {
            f"{task_type}_time_without_ai": metric("无 AI 用时 ms", None, EVENT_LOG_REQUIRED, 0, "event_log", "paired TASK_TIMER baseline duration", False, EVENT_LOG),
            f"{task_type}_time_with_ai": metric("AI 辅助用时 ms", None, EVENT_LOG_REQUIRED, 0, "event_log", "paired TASK_TIMER ai_assisted duration", False, EVENT_LOG),
            f"{task_type}_time_saved": metric("节省时间 ms", None, EVENT_LOG_REQUIRED, 0, "event_log", "baseline_time - ai_assisted_time", True, EVENT_LOG),
            f"{task_type}_time_reduction_rate": metric("时间降低率", None, EVENT_LOG_REQUIRED, 0, "event_log", "(baseline_time - ai_assisted_time) / baseline_time", True, EVENT_LOG),
        }
    baseline_avg = sum(pair["baseline"] for pair in matched) / len(matched)
    ai_avg = sum(pair["ai_assisted"] for pair in matched) / len(matched)
    saved_values = [pair["baseline"] - pair["ai_assisted"] for pair in matched]
    reduction_values = [(pair["baseline"] - pair["ai_assisted"]) / pair["baseline"] for pair in matched if pair["baseline"] != 0]
    reduction_status = OBSERVED if len(reduction_values) == len(matched) else NOT_OBSERVABLE
    return {
        f"{task_type}_time_without_ai": metric("无 AI 用时 ms", baseline_avg, OBSERVED, len(matched), "event_log", "mean paired TASK_TIMER baseline duration", False, EVENT_LOG),
        f"{task_type}_time_with_ai": metric("AI 辅助用时 ms", ai_avg, OBSERVED, len(matched), "event_log", "mean paired TASK_TIMER ai_assisted duration", False, EVENT_LOG),
        f"{task_type}_time_saved": metric("节省时间 ms", sum(saved_values) / len(saved_values), OBSERVED, len(matched), "event_log", "mean(baseline_time - ai_assisted_time)", True, EVENT_LOG),
        f"{task_type}_time_reduction_rate": metric("时间降低率", sum(reduction_values) / len(reduction_values) if reduction_values else None, reduction_status, len(reduction_values), "event_log", "mean((baseline_time - ai_assisted_time) / baseline_time)", True, EVENT_LOG),
    }


def aggregate_event_metrics(events: List[Dict[str, Any]]) -> Dict[str, Dict[str, Any]]:
    presented = {(e.get("session_id"), e.get("case_id"), e.get("item_id")) for e in events if e.get("event_type") == "KEY_INFORMATION_PRESENT"}
    viewed = {(e.get("session_id"), e.get("case_id"), e.get("item_id")) for e in events if e.get("event_type") == "KEY_INFORMATION_VIEW"}
    result: Dict[str, Dict[str, Any]] = {}
    result.update(paired_timing_metrics(events, "consultation_prep"))
    result.update(paired_timing_metrics(events, "report_review"))
    result.update(paired_timing_metrics(events, "draft_completion"))
    if "consultation_prep_time_saved" in result:
        result["prep_time_saved"] = dict(result["consultation_prep_time_saved"])
        result["prep_time_saved"]["method"] = "alias of consultation_prep_time_saved"
    if "consultation_prep_time_reduction_rate" in result:
        result["prep_time_reduction_rate"] = dict(result["consultation_prep_time_reduction_rate"])
        result["prep_time_reduction_rate"]["method"] = "alias of consultation_prep_time_reduction_rate"
    if "draft_completion_time_reduction_rate" in result:
        result["draft_time_reduction_rate"] = dict(result["draft_completion_time_reduction_rate"])
        result["draft_time_reduction_rate"]["method"] = "alias of draft_completion_time_reduction_rate"
    if not presented:
        result["key_information_view_rate"] = metric("重点信息查看率", None, EVENT_LOG_REQUIRED, 0, "event_log", "unique viewed key items / total presented key items", True, EVENT_LOG)
        result["key_information_click_rate"] = metric("重点信息点击率", None, EVENT_LOG_REQUIRED, 0, "event_log", "KEY_INFORMATION_VIEW events / total presented key items", True, EVENT_LOG)
        result["time_to_first_key_information"] = metric("首次查看重点信息时间 ms", None, EVENT_LOG_REQUIRED, 0, "event_log", "first KEY_INFORMATION_VIEW - AI_SUMMARY_VIEW", False, EVENT_LOG)
        return result
    result["key_information_view_rate"] = metric("重点信息查看率", len(viewed & presented) / len(presented), OBSERVED, len(presented), "event_log", "unique viewed key items / total presented key items", True, EVENT_LOG)
    result["key_information_click_rate"] = metric("重点信息点击率", sum(1 for e in events if e.get("event_type") == "KEY_INFORMATION_VIEW") / len(presented), OBSERVED, len(presented), "event_log", "KEY_INFORMATION_VIEW events / total presented key items", True, EVENT_LOG)
    result["time_to_first_key_information"] = metric("首次查看重点信息时间 ms", None, NOT_OBSERVABLE, 0, "event_log", "requires comparable AI_SUMMARY_VIEW and KEY_INFORMATION_VIEW timestamps", False, EVENT_LOG)
    return result
