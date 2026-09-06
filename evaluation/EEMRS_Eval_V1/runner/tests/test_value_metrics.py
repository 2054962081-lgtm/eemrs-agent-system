import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from evaluators.product_value_evaluator import (
    EVENT_LOG_REQUIRED,
    NOT_OBSERVABLE,
    OBSERVED,
    REQUIRES_HUMAN,
    aggregate_consultation_quality,
    aggregate_doctor_draft_reviews,
    aggregate_event_metrics,
    normalized_edit_distance,
    paired_timing_metrics,
    safe_ratio,
)


def assert_true(value, message):
    if not value:
        raise AssertionError(message)


def test_observed_metric_aggregation():
    rows = [
        {"evaluation": {"task_gate": "TASK_PASS", "routing": "PREFERRED", "critical_information_resolution_rate": 1.0, "turn_count": 2}},
        {"evaluation": {"task_gate": "TASK_FAIL", "routing": "ACCEPTABLE", "critical_information_resolution_rate": 0.5, "turn_count": 4}},
    ]
    summary = {
        "datasets": {"consultation": {"case_count": 2, "trace_available_count": 1}},
        "system_health": {"request_success_rate": 1.0, "p50_latency_ms": 10, "p95_latency_ms": 20},
    }
    metrics = aggregate_consultation_quality(rows, summary, "unit-run")
    assert_true(metrics["task_completion_rate"]["value"] == 0.5, "Task pass rate should aggregate observed rows")
    assert_true(metrics["preferred_routing_rate"]["value"] == 0.5, "Preferred routing should aggregate observed rows")
    assert_true(metrics["acceptable_routing_rate"]["value"] == 1.0, "Acceptable routing should include preferred")
    assert_true(metrics["trace_available_rate"]["value"] == 0.5, "Trace rate should use summary counts")


def test_no_human_data_requires_human():
    metrics = aggregate_doctor_draft_reviews([])
    assert_true(metrics["draft_acceptance_rate"]["value"] is None, "Missing human reviews should keep null")
    assert_true(metrics["draft_acceptance_rate"]["status"] == REQUIRES_HUMAN, "Missing human reviews should require human")


def test_no_event_log_requires_event_log():
    metrics = aggregate_event_metrics([])
    assert_true(metrics["key_information_view_rate"]["value"] is None, "Missing event logs should keep null")
    assert_true(metrics["key_information_view_rate"]["status"] == EVENT_LOG_REQUIRED, "Missing event logs should require event log")


def test_edit_ratio():
    assert_true(normalized_edit_distance("abc", "abc") == 0.0, "Equal text should have zero edit ratio")
    assert_true(normalized_edit_distance("abc", "axc") == 1 / 3, "One substitution over length three")
    assert_true(normalized_edit_distance("", "") is None, "Empty denominator should not be zero-filled")


def test_paired_timing():
    rows = [
        {"event_type": "TASK_TIMER", "task_type": "consultation_prep", "condition": "baseline", "case_id": "C-001", "reviewer_id": "r1", "duration_ms": 1000},
        {"event_type": "TASK_TIMER", "task_type": "consultation_prep", "condition": "ai_assisted", "case_id": "C-001", "reviewer_id": "r1", "duration_ms": 600},
    ]
    metrics = paired_timing_metrics(rows, "consultation_prep")
    assert_true(metrics["consultation_prep_time_saved"]["value"] == 400, "Time saved should be baseline minus AI-assisted")
    assert_true(metrics["consultation_prep_time_reduction_rate"]["value"] == 0.4, "Reduction rate should divide by baseline")


def test_zero_denominator():
    value, status = safe_ratio(1, 0)
    assert_true(value is None, "Zero denominator should keep null")
    assert_true(status == NOT_OBSERVABLE, "Zero denominator should be not observable")
    metrics = paired_timing_metrics([
        {"event_type": "TASK_TIMER", "task_type": "draft_completion", "condition": "baseline", "case_id": "D-001", "reviewer_id": "r1", "duration_ms": 0},
        {"event_type": "TASK_TIMER", "task_type": "draft_completion", "condition": "ai_assisted", "case_id": "D-001", "reviewer_id": "r1", "duration_ms": 10},
    ], "draft_completion")
    assert_true(metrics["draft_completion_time_reduction_rate"]["status"] == NOT_OBSERVABLE, "Zero baseline should not divide")
    assert_true(metrics["draft_completion_time_without_ai"]["status"] == OBSERVED, "Raw paired time is still observed")


def main():
    test_observed_metric_aggregation()
    test_no_human_data_requires_human()
    test_no_event_log_requires_event_log()
    test_edit_ratio()
    test_paired_timing()
    test_zero_denominator()
    print("Value Metrics Unit Test PASS")


if __name__ == "__main__":
    main()
