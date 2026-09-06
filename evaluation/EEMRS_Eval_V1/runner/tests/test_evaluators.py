import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from core.dataset_loader import DatasetLoader
from core.config_loader import task_timeout
from core.report_fixture_adapter import ReportFixtureAdapter
from core.preflight import unavailable_dependency
from tools.run_eval import _agent_run_id_from_output, _root_cause_for_error
from adapters import report_adapter
from evaluators import consultation_evaluator, doctor_draft_evaluator, report_evaluator, safety_evaluator
from evaluators.department_canonicalizer import canonicalize_department


def first(dataset):
    return next(row for name, row in DatasetLoader().load("dev") if name == dataset)


def assert_true(value, message):
    if not value:
        raise AssertionError(message)


def test_report_numeric_and_trend():
    case = first("report")
    output = report_evaluator.oracle_output(case)
    ev = report_evaluator.evaluate(case, output)
    assert_true(ev["numeric_accuracy"], "Report numeric oracle should pass")
    assert_true(ev["trend_accuracy"], "Report trend oracle should pass")
    output["trend_class"] = "BROKEN"
    ev = report_evaluator.evaluate(case, output)
    assert_true(not ev["trend_accuracy"], "Broken trend should fail")


def test_report_api_trend_items_adapter_and_evaluator():
    case = first("report")
    output = {
        "status": "SUCCESS",
        "trendItems": [{
            "code": "WBC",
            "name": "白细胞",
            "unit": "10^9/L",
            "pointCount": 3,
            "firstDate": "2026-01-11",
            "latestDate": "2026-03-11",
            "firstValue": 8,
            "latestValue": 16,
            "previousValue": 12,
            "changeAbsolute": 8,
            "changePercent": 100.0,
            "trendDirection": "INCREASING",
        }],
    }

    normalized = report_adapter.normalize_output(output)
    ev = report_evaluator.evaluate(case, output)

    assert_true(normalized["normalized_indicator"] == "WBC", "Adapter should read API trendItems code")
    assert_true(normalized["normalized_unit"] == "10^9/L", "Adapter should read API trendItems unit")
    assert_true(normalized["numeric_change"] == 8.0, "Adapter should map first-to-latest change")
    assert_true(ev["indicator_matching"], "Evaluator should compare deterministic indicator")
    assert_true(ev["numeric_accuracy"], "Evaluator should compare deterministic numeric change")
    assert_true(ev["trend_accuracy"], "Evaluator should compare deterministic trend")


def test_r001_frozen_truth_regression():
    case = next(row for name, row in DatasetLoader().load("dev") if name == "report" and row["metadata"]["case_id"] == "R-001")
    bundle = case["expected_clean_bundle"]
    values = [point["value"] for point in bundle]
    dates = [point["date"] for point in bundle]

    assert_true(case["normalized_indicator"] == "WBC", "R-001 indicator should be WBC")
    assert_true(values == [8, 12, 16], "R-001 frozen values should stay 8/12/16")
    assert_true(dates == ["2026-01-11", "2026-02-11", "2026-03-11"], "R-001 frozen dates should stay sorted")
    assert_true(case["numeric_change"] == 8.0, "R-001 expected numeric change should be first-to-latest")


def test_multi_indicator():
    case = next(row for name, row in DatasetLoader().load("dev") if name == "report" and row["metadata"]["report_slice"] == "MULTI_INDICATOR")
    assert_true(len(case.get("per_indicator_ground_truth", {})) >= 2, "Multi indicator GT should cover >=2 indicators")


def test_consultation_task_gate():
    case = first("consultation")
    conversation = [{"role": "user", "content": case["initial_user_input"]["text"]}]
    for slot in case["information_requirements"]:
        if slot["priority"] == "CRITICAL" and slot.get("initial_resolution_state") == "ACTIVE_UNRESOLVED":
            conversation.append({"role": "assistant", "content": "请补充 " + slot["canonical_slot"]})
    ev = consultation_evaluator.evaluate(case, conversation, {"recommendedDepartment": case["product_ground_truth"]["preferred_department"]})
    assert_true(ev["task_gate"] == "TASK_PASS", "Oracle consultation should pass task gate")
    ev = consultation_evaluator.evaluate(case, [], {"recommendedDepartment": "Dental"})
    assert_true(ev["task_gate"] == "TASK_FAIL", "Broken consultation should fail task gate")


def test_department_canonicalizer():
    assert_true(canonicalize_department("消化内科") == "Gastroenterology", "Digestive department should canonicalize")
    assert_true(canonicalize_department("普外科") == "General Surgery", "General surgery alias should canonicalize")
    assert_true(canonicalize_department("普通外科") == "General Surgery", "Full Chinese general surgery should canonicalize")
    assert_true(canonicalize_department("急诊科") == "Emergency", "Emergency should match benchmark taxonomy")
    assert_true(canonicalize_department("心内科") == "Cardiology", "Cardiology should canonicalize")
    assert_true(canonicalize_department("泌尿外科") == "Urology", "Urology should canonicalize")
    assert_true(canonicalize_department("精神心理科") == "Psychiatry", "Mental health department should match benchmark psychiatry taxonomy")
    assert_true(canonicalize_department("心理科") == "心理科", "Unaudited psychology labels should not be collapsed into Psychiatry")
    assert_true(canonicalize_department("妇产科") == "妇产科", "OB/GYN has no current benchmark canonical label and should stay safe")
    assert_true(canonicalize_department("未知科室") == "未知科室", "Unknown departments should not be invented")


def test_consultation_routing_canonicalization():
    case = next(row for name, row in DatasetLoader().load("dev") if name == "consultation" and row["metadata"]["case_id"] == "C-001")
    conversation = []
    for slot in case["information_requirements"]:
        if slot["priority"] == "CRITICAL" and slot.get("initial_resolution_state") == "ACTIVE_UNRESOLVED":
            conversation.append({"role": "assistant", "content": "请补充 " + slot["canonical_slot"] + " " + " ".join(slot.get("acceptable_semantics", []))})
    ev = consultation_evaluator.evaluate(case, conversation, {"recommendedDepartment": "消化内科", "urgency": "urgent"})
    assert_true(ev["routing"] == "PREFERRED", "Chinese API department should match canonical preferred department")
    assert_true(ev["task_gate"] == "TASK_PASS", "Canonicalized routing plus critical slots should pass")


def test_mental_department_alias_and_non_alias_routing():
    case = next(row for name, row in DatasetLoader().load("dev") if name == "consultation" and row["metadata"]["case_id"] == "C-019")
    conversation = []
    for slot in case["information_requirements"]:
        if slot["priority"] == "CRITICAL" and slot.get("initial_resolution_state") == "ACTIVE_UNRESOLVED":
            conversation.append({"role": "assistant", "content": "请补充 " + slot["canonical_slot"] + " " + " ".join(slot.get("acceptable_semantics", []))})

    ev = consultation_evaluator.evaluate(case, conversation, {"recommendedDepartment": "精神心理科"})
    assert_true(ev["routing"] == "PREFERRED", "精神心理科 should map to Psychiatry for C-019")
    assert_true(ev["task_gate"] == "TASK_PASS", "Mental alias plus resolved critical slots should pass")

    ev = consultation_evaluator.evaluate(case, conversation, {"recommendedDepartment": "心理科"})
    assert_true(ev["routing"] == "UNACCEPTABLE", "心理科 is not an audited alias and should remain unacceptable")


def test_safety_gate():
    case = first("safety")
    ev = safety_evaluator.evaluate(case, {"reply": "遵守安全边界"})
    assert_true(ev["safety_status"] == "SAFETY_PASS", "Safety benign output should pass")


def test_doctor_draft():
    case = first("doctor_draft")
    output = {field: "x" for field in case["ground_truth"]["required_fields"]}
    ev = doctor_draft_evaluator.evaluate(case, output)
    assert_true(ev["structured_output_success"], "Draft structured output should pass")


def test_doctor_draft_failed_response_is_not_structured_success():
    case = first("doctor_draft")
    output = {
        "success": False,
        "record": None,
        "errorCode": "MODEL_RESPONSE_JSON_INVALID",
        "error": "云端模型返回内容格式异常，请稍后重试。",
    }

    ev = doctor_draft_evaluator.evaluate(case, output)

    assert_true(not ev["structured_output_success"], "Failed wrapper should not be structured success")
    assert_true(ev["required_field_completeness"] == 0.0, "Failed wrapper should not receive field credit")
    assert_true(ev["source_mapping_coverage"] == 0.0, "Failed wrapper should not receive source coverage credit")
    assert_true(ev["case_status"] == "TASK_FAIL", "Failed wrapper should remain task fail")


def test_timeout_policy():
    config = {"timeout_seconds": 2, "task_timeouts_seconds": {"report": 15, "consultation": 60}}
    assert_true(task_timeout(config, "report") == 15, "Report timeout should be task-level")
    assert_true(task_timeout(config, "consultation") == 60, "Consultation timeout should be task-level")
    assert_true(task_timeout(config, "doctor_draft") == 2, "Missing task timeout should fall back")


def test_error_taxonomy():
    assert_true(_root_cause_for_error("CLIENT_TIMEOUT") == "CLIENT_TIMEOUT", "Client timeout should stay distinct")
    assert_true(_root_cause_for_error("CONNECTION_REFUSED") == "INFRA_DEPENDENCY_UNAVAILABLE", "Connection refused should be infra")
    assert_true(_root_cause_for_error("HTTP_5XX") == "HTTP_5XX", "HTTP 5xx should stay distinct")


def test_dependency_preflight_mapping():
    config = {"dependency_map": {"consultation": ["agent_server", "rag_service"], "doctor_draft": ["agent_server"]}}
    preflight = {"checks": {"agent_server": {"available": True}, "rag_service": {"available": False}}}
    assert_true(unavailable_dependency("consultation", preflight, config) == "rag_service", "Consultation should depend on RAG")
    assert_true(unavailable_dependency("doctor_draft", preflight, config) is None, "Doctor draft should not be blocked by RAG")


def test_report_fixture_payload():
    case = first("report")
    adapter = ReportFixtureAdapter("http://localhost:8081")
    payload = adapter._fixture_payload(case, "unit-run")
    assert_true(payload["caseId"] == case["metadata"]["case_id"], "Fixture should preserve case id")
    assert_true(payload["patientId"] == "eval-" + case["metadata"]["case_id"], "Fixture patient id should match report request")
    assert_true(len(payload["records"]) >= 2, "Fixture should include report bundle records")


def test_run_id_extraction():
    assert_true(_agent_run_id_from_output({"traceRunId": "run-1"}) == "run-1", "Report traceRunId should be extracted")
    assert_true(_agent_run_id_from_output({"agentRunId": "run-2"}) == "run-2", "Generic agentRunId should be extracted")


def test_conversation_round_evidence_shape():
    from core.result_models import CaseResult
    result = CaseResult(case_id="C-X", dataset="consultation", split="DEV", input={})
    result.conversation_rounds.append({
        "round": 1,
        "request_id": "req-1",
        "agent_run_id": "run-1",
        "trace_status": "TRACE_AVAILABLE",
        "trace_path": "traces/C-X_round_1_trace_detail.json",
    })
    row = result.to_dict()
    assert_true(row["conversation_rounds"][0]["agent_run_id"] == "run-1", "Round evidence should serialize run id")


def main():
    test_report_numeric_and_trend()
    test_report_api_trend_items_adapter_and_evaluator()
    test_r001_frozen_truth_regression()
    test_multi_indicator()
    test_consultation_task_gate()
    test_department_canonicalizer()
    test_consultation_routing_canonicalization()
    test_mental_department_alias_and_non_alias_routing()
    test_safety_gate()
    test_doctor_draft()
    test_doctor_draft_failed_response_is_not_structured_success()
    test_timeout_policy()
    test_error_taxonomy()
    test_dependency_preflight_mapping()
    test_report_fixture_payload()
    test_run_id_extraction()
    test_conversation_round_evidence_shape()
    print("Evaluator Unit Test PASS")


if __name__ == "__main__":
    main()
