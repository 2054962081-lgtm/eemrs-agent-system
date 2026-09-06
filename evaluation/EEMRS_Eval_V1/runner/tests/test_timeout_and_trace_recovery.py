import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from core.agent_runner import AgentRequestError
from core.config_loader import load_config, task_timeout
from core.trace_collector import TraceCollector
from tools.run_eval import aggregate, run_http_case


def assert_true(value, message):
    if not value:
        raise AssertionError(message)


def test_task_specific_timeouts_keep_non_report_tasks_unchanged():
    config = load_config(ROOT / "config" / "eval_config.json")

    assert_true(task_timeout(config, "consultation") == 60, "Consultation timeout should remain 60s")
    assert_true(task_timeout(config, "safety") == 60, "Safety timeout should remain 60s")
    assert_true(task_timeout(config, "doctor_draft") == 60, "Doctor Draft timeout should remain 60s")
    assert_true(task_timeout(config, "report") == 60, "Report timeout should use its task-specific 60s window")


def test_report_request_uses_task_specific_timeout_above_twenty_seconds():
    runner = SlowReportRunner()
    fixture = CountingFixtureAdapter()

    result = run_http_case(
        "report",
        _report_case(),
        runner,
        _config(),
        "timeout-contract",
        NoopTraceCollector(),
        fixture,
        _preflight_pass(),
        Path(tempfile.mkdtemp()),
    )

    assert_true(runner.timeout_seconds == 60, "Report runner should receive 60s, not 15s")
    assert_true(runner.business_request_count == 1, "Report request should be called once")
    assert_true(result.execution_status == "EXECUTED", "20s fake report should not client-timeout at 15s")
    assert_true(result.agent_run_id == "run-report-ok", "Normal response should still expose agent_run_id")
    assert_true(fixture.prepare_count == 1, "Fixture should prepare once")
    assert_true(fixture.cleanup_count == 1, "Fixture should cleanup once")


def test_timeout_keeps_request_failed_but_recovers_trace_by_request_id():
    output_dir = Path(tempfile.mkdtemp())
    trace_collector = RecoveringTraceCollector()
    fixture = CountingFixtureAdapter()

    result = run_http_case(
        "report",
        _report_case(),
        TimeoutReportRunner(),
        _config(),
        "trace-recovery",
        trace_collector,
        fixture,
        _preflight_pass(),
        output_dir,
    )

    assert_true(result.execution_status == "REQUEST_FAILED", "Timeout should remain a request failure")
    assert_true(result.root_cause == "CLIENT_TIMEOUT", "Root cause should stay CLIENT_TIMEOUT")
    assert_true(result.trace_status == "TRACE_RECOVERED", "Trace should be recovered via request_id")
    assert_true(result.agent_run_id == "run-recovered", "Recovered trace should supply agent_run_id")
    assert_true(trace_collector.request_id.startswith("trace-recovery-R-001-"), "Runner request_id should drive lookup")
    assert_true((output_dir / "traces" / "R-001_trace_detail.json").exists(), "Recovered trace detail should be saved")
    assert_true(fixture.prepare_count == 1, "Fixture should prepare once before timeout")
    assert_true(fixture.cleanup_count == 1, "Fixture should cleanup once after timeout")


def test_trace_collector_uses_request_id_lookup_path():
    collector = TraceCollector("http://trace.test", timeout_seconds=1, poll_interval_ms=1)
    seen = {}

    def fake_fetch(url):
        seen["url"] = url
        return {"status": "TRACE_AVAILABLE", "detail": {"run": {"runId": "run-1"}}}

    collector._fetch = fake_fetch
    result = collector.get_detail_by_request_id("eval/report request 001")

    assert_true(result["status"] == "TRACE_RECOVERED", "Request lookup should mark recovered trace")
    assert_true("/api/agent-traces/lookup/request/" in seen["url"], "Lookup endpoint should be used")
    assert_true("eval%2Freport%20request%20001" in seen["url"], "Request id should be URL encoded")


def test_trace_collector_accepts_multi_round_request_lookup_shape():
    collector = TraceCollector("http://trace.test", timeout_seconds=1, poll_interval_ms=1)

    def fake_fetch(url):
        return {
            "status": "TRACE_AVAILABLE",
            "detail": {
                "run": {"runId": "run-round-2"},
                "runs": [{"runId": "run-round-1"}, {"runId": "run-round-2"}],
                "details": [
                    {"run": {"runId": "run-round-1"}, "steps": []},
                    {"run": {"runId": "run-round-2"}, "steps": []},
                ],
            },
        }

    collector._fetch = fake_fetch
    result = collector.get_detail_by_request_id("eval-C-004")

    assert_true(result["status"] == "TRACE_RECOVERED", "Request lookup should still recover multi-round traces")
    assert_true(result["detail"]["run"]["runId"] == "run-round-2", "Latest run remains available for old callers")
    assert_true(len(result["detail"]["runs"]) == 2, "All request runs should remain observable")


def test_trace_collector_reports_controller_failure_without_polling_until_timeout():
    collector = TraceCollector("http://trace.test", timeout_seconds=1, poll_interval_ms=1)

    def fake_fetch(url, lookup_mode="run_id"):
        return {
            "status": "TRACE_CONTROLLER_FAILED",
            "detail": None,
            "trace_http_status": 500,
            "trace_error_code": "TRACE_CONTROLLER_FAILED",
            "trace_error_message": "No static resource api/agent-traces/runs/run-1/detail.",
            "trace_lookup_mode": lookup_mode,
        }

    collector._fetch = fake_fetch
    result = collector.get_detail("run-1")

    assert_true(result["status"] == "TRACE_CONTROLLER_FAILED", "Controller failure should stay distinct")
    assert_true(result["trace_http_status"] == 500, "HTTP status should be preserved")
    assert_true(result["trace_lookup_mode"] == "run_id", "Lookup mode should be preserved")


def test_trace_recovered_counts_as_available_in_summary():
    summary = aggregate([
        {"dataset": "report", "trace_status": "TRACE_RECOVERED", "execution_status": "EXECUTED", "evaluation": {}},
        {"dataset": "report", "trace_status": "TRACE_NOT_FOUND", "execution_status": "EXECUTED", "evaluation": {}},
    ])

    assert_true(summary["datasets"]["report"]["trace_available_count"] == 1, "Recovered trace should count as available")


def _config():
    return {
        "agent_base_url": "http://agent.test",
        "task_timeouts_seconds": {
            "report": 60,
            "consultation": 60,
            "safety": 60,
            "doctor_draft": 60,
        },
        "preflight_enabled": True,
    }


def _report_case():
    return {
        "metadata": {"case_id": "R-001", "split": "DEV"},
        "input": {"patient_id": "eval-R-001", "report_type": "LAB"},
        "normalized_indicator": "WBC",
        "expected_clean_bundle": [
            {"date": "2026-01-11", "value": 8},
            {"date": "2026-02-11", "value": 12},
            {"date": "2026-03-11", "value": 16},
        ],
    }


def _preflight_pass():
    return {"status": "PASS", "checks": {"agent_server": {"available": True}, "report_fixture": {"available": True}}}


class CountingFixtureAdapter:
    def __init__(self):
        self.prepare_count = 0
        self.cleanup_count = 0

    def prepare(self, case, eval_run_id):
        self.prepare_count += 1

    def cleanup(self, case_id, eval_run_id):
        self.cleanup_count += 1


class NoopTraceCollector:
    def get_detail(self, run_id):
        return {"status": "TRACE_AVAILABLE", "detail": {"run": {"runId": run_id}, "steps": [], "tool_calls": []}}

    def get_detail_by_request_id(self, request_id):
        return {"status": "TRACE_NOT_FOUND", "detail": None}


class RecoveringTraceCollector:
    def __init__(self):
        self.request_id = ""

    def get_detail(self, run_id):
        raise AssertionError("Recovered timeout traces should not require response-derived run polling")

    def get_detail_by_request_id(self, request_id):
        self.request_id = request_id
        return {
            "status": "TRACE_RECOVERED",
            "detail": {"run": {"runId": "run-recovered"}, "steps": [], "tool_calls": []},
        }


class SlowReportRunner:
    def __init__(self):
        self.timeout_seconds = None
        self.business_request_count = 0

    def run_report_trend(self, payload, timeout_seconds, correlation):
        self.timeout_seconds = timeout_seconds
        self.business_request_count += 1
        assert_true(timeout_seconds > 20, "Report timeout should allow a 20s request")
        return {
            "http_status": 200,
            "agent_run_id": "run-report-ok",
            "raw": {"success": True, "data": {"status": "SUCCESS", "traceRunId": "run-report-ok"}},
        }


class TimeoutReportRunner:
    def run_report_trend(self, payload, timeout_seconds, correlation):
        raise AgentRequestError("CLIENT_TIMEOUT", "timed out")
