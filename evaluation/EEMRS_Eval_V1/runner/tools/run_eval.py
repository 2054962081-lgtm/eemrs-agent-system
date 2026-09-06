import argparse
import json
import os
import sys
import time
import uuid
from datetime import datetime
from pathlib import Path
from typing import Any, Dict, List, Tuple

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from adapters import consultation_adapter, doctor_draft_adapter, report_adapter, safety_adapter
from core.agent_runner import AgentRequestError, AgentRunner, Correlation
from core.config_loader import load_config as load_runner_config, task_timeout
from core.dataset_loader import DatasetLoader
from core.preflight import run_preflight, unavailable_dependency
from core.report_fixture_adapter import ReportFixtureAdapter, ReportFixtureError
from core.result_models import REQUEST_FAILED, SKIPPED_INFRA, CaseResult
from core.run_context import run_manifest
from core.trace_collector import TraceCollector
from evaluators import consultation_evaluator, doctor_draft_evaluator, planning_evaluator, rag_evaluator, report_evaluator, safety_evaluator
from evaluators.system_health_evaluator import summarize as system_health


BASE = ROOT.parent
CONFIG = ROOT / "config" / "eval_config.json"
RESULTS = ROOT / "results"


def load_config() -> Dict[str, Any]:
    config = load_runner_config(CONFIG)
    override = os.environ.get("EVAL_REPORT_TIMEOUT_SECONDS")
    if override:
        config.setdefault("task_timeouts_seconds", {})["report"] = int(override)
    return config


def write_jsonl(path: Path, rows: List[Dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("".join(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n" for row in rows), encoding="utf-8")


def write_json(path: Path, value: Dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding="utf-8")


def read_json(path: Path) -> Dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def append_text(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a", encoding="utf-8") as handle:
        handle.write(text)


def unwrap(raw: Dict[str, Any]) -> Dict[str, Any]:
    data = raw.get("data") if isinstance(raw, dict) else None
    return data if isinstance(data, dict) else raw


def run_http_case(
        dataset: str,
        case: Dict[str, Any],
        runner: AgentRunner,
        config: Dict[str, Any],
        eval_run_id: str,
        trace_collector: TraceCollector,
        fixture_adapter: ReportFixtureAdapter,
        preflight: Dict[str, Any],
        output_dir: Path,
) -> CaseResult:
    cid = case["metadata"]["case_id"]
    request_id = f"{eval_run_id}-{cid}-{uuid.uuid4().hex[:8]}"
    correlation = Correlation(eval_run_id=eval_run_id, case_id=cid, request_id=request_id)
    start = time.time()
    result = CaseResult(
        case_id=cid,
        dataset=dataset,
        split=case["metadata"]["split"],
        input={"case_id": cid},
        eval_run_id=eval_run_id,
        request_id=request_id,
    )
    logs_dir = output_dir / "logs"
    traces_dir = output_dir / "traces"
    runner_log = logs_dir / f"{cid}_runner.log"
    append_text(runner_log, "\n".join([
        f"case_id: {cid}",
        f"dataset: {dataset}",
        f"eval_run_id: {eval_run_id}",
        f"request_id: {request_id}",
        f"start_timestamp: {datetime.now().isoformat(timespec='seconds')}",
        f"agent_base_url: {config['agent_base_url']}",
        f"timeout_seconds: {task_timeout(config, dataset)}",
        "",
    ]))
    blocked_dependency = unavailable_dependency(dataset, preflight, config) if config.get("preflight_enabled", True) else None
    if blocked_dependency:
        result.execution_status = SKIPPED_INFRA
        result.dependency = blocked_dependency
        result.error = {"type": "INFRA_DEPENDENCY_UNAVAILABLE", "dependency": blocked_dependency}
        result.root_cause = "INFRA_DEPENDENCY_UNAVAILABLE"
        result.latency["total_ms"] = 0
        return result
    fixture_prepared = False
    try:
        if dataset == "report":
            try:
                append_text(runner_log, "fixture_preparation: START\n")
                fixture_adapter.prepare(case, eval_run_id)
                fixture_prepared = True
                append_text(runner_log, "fixture_preparation: SUCCESS\n")
            except ReportFixtureError as exc:
                append_text(runner_log, f"fixture_preparation: FAILED {str(exc)}\n")
                result.execution_status = REQUEST_FAILED
                result.error = {"type": "REPORT_FIXTURE_FAILURE", "message": str(exc)}
                result.root_cause = "REPORT_FIXTURE_FAILURE"
                result.latency["total_ms"] = int((time.time() - start) * 1000)
                return result
            payload = report_adapter.to_payload(case)
            append_text(runner_log, f"request_url: {config['agent_base_url'].rstrip('/')}/api/agent/report-trend/analyze\n")
            response = runner.run_report_trend(payload, task_timeout(config, dataset), correlation)
            result.latency["last_http_status"] = response.get("http_status")
            result.model_output = unwrap(response["raw"])
        elif dataset == "safety":
            payload = safety_adapter.to_payload(case)
            append_text(runner_log, f"request_url: {config['agent_base_url'].rstrip('/')}/api/agent/pre-consultation\n")
            response = runner.run_pre_consultation(payload, task_timeout(config, dataset), correlation)
            result.latency["last_http_status"] = response.get("http_status")
            result.model_output = unwrap(response["raw"])
        elif dataset == "doctor_draft":
            payload = doctor_draft_adapter.to_payload(case)
            append_text(runner_log, f"request_url: {config['agent_base_url'].rstrip('/')}/api/agent/medical-record-drafts/generate\n")
            response = runner.run_doctor_draft(payload, task_timeout(config, dataset), correlation)
            result.latency["last_http_status"] = response.get("http_status")
            result.model_output = unwrap(response["raw"])
        else:
            history = [{"role": "user", "content": case["initial_user_input"]["text"]}]
            simulator = consultation_adapter.ScriptedPatientSimulator(case)
            final_output = {}
            conversation = [history[0]]
            round_evidence = []
            for round_no in range(1, config.get("max_turns", 8) + 1):
                payload = consultation_adapter.to_initial_payload(case, f"eval-{cid}", round_no, history)
                append_text(runner_log, f"request_url: {config['agent_base_url'].rstrip('/')}/api/agent/pre-consultation round={round_no}\n")
                response = runner.run_pre_consultation(payload, task_timeout(config, dataset), correlation)
                result.latency["last_http_status"] = response.get("http_status")
                final_output = unwrap(response["raw"])
                round_agent_run_id = response.get("agent_run_id") or _agent_run_id_from_output(final_output)
                round_record = {
                    "round": round_no,
                    "request_id": request_id,
                    "agent_run_id": round_agent_run_id or "",
                    "trace_status": "NOT_OBSERVABLE",
                    "trace_path": "",
                }
                if round_agent_run_id:
                    trace_result = trace_collector.get_detail(round_agent_run_id)
                    round_record["trace_status"] = trace_result["status"]
                    _copy_trace_meta(round_record, trace_result)
                    detail = trace_result.get("detail")
                    if isinstance(detail, dict):
                        round_trace_path = traces_dir / f"{cid}_round_{round_no}_trace_detail.json"
                        write_json(round_trace_path, detail)
                        round_record["trace_path"] = str(round_trace_path)
                round_evidence.append(round_record)
                reply = final_output.get("reply", "")
                conversation.append({"role": "assistant", "content": reply})
                should_stop = final_output.get("shouldStopConversation")
                if should_stop is None:
                    should_stop = final_output.get("finished")
                if should_stop:
                    break
                patient_reply = simulator.reply(reply)
                history.extend([{"role": "assistant", "content": reply}, {"role": "user", "content": patient_reply}])
                conversation.append({"role": "user", "content": patient_reply})
            result.agent_run_id = next((item["agent_run_id"] for item in reversed(round_evidence) if item.get("agent_run_id")), "")
            result.conversation_rounds = round_evidence
            result.conversation_trace = conversation
            result.model_output = final_output
        result.agent_run_id = result.agent_run_id or response.get("agent_run_id") or _agent_run_id_from_output(result.model_output)
        result.latency["total_ms"] = int((time.time() - start) * 1000)
        if isinstance(result.model_output, dict) and result.model_output.get("status") == "FAILED":
            result.error = {
                "type": "OUTPUT_GENERATION_FAILURE",
                "message": result.model_output.get("errorMessage") or result.model_output.get("errorCode") or "service returned FAILED",
                "error_code": result.model_output.get("errorCode"),
            }
            result.root_cause = "OUTPUT_GENERATION_FAILURE" if dataset in {"report", "doctor_draft"} else "SYSTEM_ERROR"
    except AgentRequestError as exc:
        result.execution_status = REQUEST_FAILED
        result.error = {"type": exc.error_type, "message": str(exc), "http_status": exc.status_code, "response_body": exc.response_body}
        result.root_cause = _root_cause_for_error(exc.error_type)
        result.latency["total_ms"] = int((time.time() - start) * 1000)
    except Exception as exc:
        result.execution_status = REQUEST_FAILED
        result.error = {"type": "EVALUATION_FAILURE", "message": str(exc)}
        result.root_cause = "EVALUATION_FAILURE"
        result.latency["total_ms"] = int((time.time() - start) * 1000)
    finally:
        if not result.agent_run_id and result.root_cause == "CLIENT_TIMEOUT":
            trace_result = trace_collector.get_detail_by_request_id(request_id)
            detail = trace_result.get("detail")
            if isinstance(detail, dict):
                run = detail.get("run") or {}
                result.agent_run_id = run.get("runId") or run.get("run_id") or ""
                result.trace_status = trace_result["status"]
                _copy_trace_meta(result.latency, trace_result)
                write_json(traces_dir / f"{cid}_trace_detail.json", detail)
                append_text(runner_log, f"trace_recovery_by_request_id: {result.trace_status}\n")
                append_text(runner_log, _trace_meta_log(trace_result))
                append_text(runner_log, f"trace_detail_file: {traces_dir / f'{cid}_trace_detail.json'}\n")
                result.retrieval_trace = _steps_by_type(detail, "RAG")
                result.retrieval_trace_refs = _retrieval_trace_refs(detail)
                result.planning_trace = _steps_by_type(detail, "QUESTION_PLAN")
                result.state_trace = _steps_by_type(detail, "SESSION_STATE")
                result.safety_trace = _steps_by_type(detail, "SAFETY")
            else:
                append_text(runner_log, f"trace_recovery_by_request_id: {trace_result.get('status', 'NOT_OBSERVABLE')}\n")
                append_text(runner_log, _trace_meta_log(trace_result))
        if result.agent_run_id:
            if result.trace_status != "TRACE_RECOVERED":
                trace_result = trace_collector.get_detail(result.agent_run_id)
                result.trace_status = trace_result["status"]
                _copy_trace_meta(result.latency, trace_result)
                append_text(runner_log, f"trace_polling_result: {result.trace_status}\n")
                append_text(runner_log, _trace_meta_log(trace_result))
                detail = trace_result.get("detail")
                if isinstance(detail, dict):
                    write_json(traces_dir / f"{cid}_trace_detail.json", detail)
                    append_text(runner_log, f"trace_detail_file: {traces_dir / f'{cid}_trace_detail.json'}\n")
                    result.retrieval_trace = _steps_by_type(detail, "RAG")
                    result.retrieval_trace_refs = _retrieval_trace_refs(detail)
                    result.planning_trace = _steps_by_type(detail, "QUESTION_PLAN")
                    result.state_trace = _steps_by_type(detail, "SESSION_STATE")
                    result.safety_trace = _steps_by_type(detail, "SAFETY")
                else:
                    append_text(runner_log, f"trace_error: {trace_result.get('error', 'NOT_OBSERVABLE')}\n")
        if fixture_prepared:
            try:
                fixture_adapter.cleanup(cid, eval_run_id)
                append_text(runner_log, "fixture_cleanup: SUCCESS\n")
            except ReportFixtureError:
                result.evaluation["fixture_cleanup"] = "FAILED"
                append_text(runner_log, "fixture_cleanup: FAILED\n")
        append_text(runner_log, "\n".join([
            f"end_timestamp: {datetime.now().isoformat(timespec='seconds')}",
            f"agent_run_id: {result.agent_run_id or 'NOT_OBSERVABLE'}",
            f"http_status: {result.latency.get('last_http_status', 'NOT_OBSERVABLE')}",
            f"latency_ms: {result.latency.get('total_ms', 'NOT_OBSERVABLE')}",
            f"execution_status: {result.execution_status}",
            f"root_cause: {result.root_cause}",
            "",
        ]))
    return result


def _agent_run_id_from_output(output: Dict[str, Any]) -> str:
    if not isinstance(output, dict):
        return ""
    return output.get("traceRunId") or output.get("agentRunId") or ""


def _copy_trace_meta(target: Dict[str, Any], trace_result: Dict[str, Any]) -> None:
    for key in ("trace_http_status", "trace_error_code", "trace_error_message", "trace_lookup_mode"):
        if trace_result.get(key) is not None:
            target[key] = trace_result.get(key)


def _trace_meta_log(trace_result: Dict[str, Any]) -> str:
    lines = []
    for key in ("trace_http_status", "trace_error_code", "trace_error_message", "trace_lookup_mode"):
        if trace_result.get(key) is not None:
            lines.append(f"{key}: {trace_result.get(key)}")
    return ("\n".join(lines) + "\n") if lines else ""


def _root_cause_for_error(error_type: str) -> str:
    if error_type in {"CLIENT_TIMEOUT", "UPSTREAM_TIMEOUT"}:
        return error_type
    if error_type in {"CONNECTION_REFUSED", "DEPENDENCY_UNAVAILABLE"}:
        return "INFRA_DEPENDENCY_UNAVAILABLE"
    if error_type in {"HTTP_4XX", "HTTP_5XX"}:
        return error_type
    return "SYSTEM_ERROR"


def _steps_by_type(detail: Dict[str, Any], prefix: str) -> Any:
    steps = detail.get("steps") or []
    matched = [step for step in steps if prefix in str(step.get("stepType", step.get("type", "")))]
    return matched or None


def _retrieval_trace_refs(detail: Dict[str, Any]) -> List[Dict[str, Any]]:
    refs = []
    for step in detail.get("steps") or []:
        if "RAG" not in str(step.get("stepType", step.get("type", ""))):
            continue
        refs.append({
            "trace_id": step.get("traceId") or step.get("trace_id"),
            "run_id": step.get("runId") or step.get("run_id"),
            "step_id": step.get("stepId") or step.get("step_id"),
            "step_type": step.get("stepType") or step.get("type"),
            "step_name": step.get("stepName") or step.get("name"),
            "status": step.get("status"),
        })
    return refs


def _parse_json_field(value: Any) -> Any:
    if not value:
        return None
    if isinstance(value, (dict, list)):
        return value
    try:
        return json.loads(value)
    except Exception:
        return None


def run_oracle_case(dataset: str, case: Dict[str, Any], broken: bool = False) -> CaseResult:
    cid = case["metadata"]["case_id"]
    result = CaseResult(case_id=cid, dataset=dataset, split=case["metadata"]["split"], input={"case_id": cid})
    if dataset == "report":
        output = report_evaluator.oracle_output(case)
        if broken:
            output["trend_class"] = "STABLE"
        result.model_output = output
    elif dataset == "consultation":
        conversation = [{"role": "user", "content": case["initial_user_input"]["text"]}]
        for slot in case["information_requirements"]:
            if slot["priority"] == "CRITICAL" and slot.get("initial_resolution_state") == "ACTIVE_UNRESOLVED":
                if broken and not result.conversation_trace:
                    continue
                conversation.append({"role": "assistant", "content": "请补充 " + slot["canonical_slot"]})
        result.conversation_trace = conversation
        result.model_output = {"recommendedDepartment": case["product_ground_truth"]["preferred_department"]}
        if broken:
            result.model_output = {"recommendedDepartment": "Dental"}
    elif dataset == "safety":
        result.model_output = {"reply": "遵守安全边界并给出合适建议"}
        if broken:
            result.model_output = {"reply": "给出确定诊断和处方剂量"}
    else:
        output = {field: "已生成" for field in case["ground_truth"].get("required_fields", [])}
        if broken:
            output = {}
        result.model_output = output
    result.latency["total_ms"] = 0
    return result


def evaluate_case(dataset: str, case: Dict[str, Any], result: CaseResult) -> CaseResult:
    if result.execution_status == SKIPPED_INFRA:
        result.evaluation["case_status"] = "SKIPPED_INFRA"
        return result
    if dataset == "report":
        result.evaluation.update(report_evaluator.evaluate(case, result.model_output))
    elif dataset == "consultation":
        result.evaluation.update(consultation_evaluator.evaluate(case, result.conversation_trace, result.model_output))
        result.evaluation.update(planning_evaluator.evaluate(case, result.planning_trace))
        result.evaluation.update(rag_evaluator.evaluate(case, result.retrieval_trace))
    elif dataset == "safety":
        result.evaluation.update(safety_evaluator.evaluate(case, result.model_output))
    elif dataset == "doctor_draft":
        result.evaluation.update(doctor_draft_evaluator.evaluate(case, result.model_output))
    if result.error:
        result.evaluation["case_status"] = "SYSTEM_ERROR"
    return result


def select_cases(rows: List[Tuple[str, Dict[str, Any]]], phase: str, limit: int | None, case_ids: List[str] | None = None) -> List[Tuple[str, Dict[str, Any]]]:
    if case_ids:
        wanted = set(case_ids)
        return [item for item in rows if item[1]["metadata"]["case_id"] in wanted]
    if phase == "smoke":
        picked = []
        for dataset in ["consultation", "safety", "report", "doctor_draft"]:
            picked.extend([item for item in rows if item[0] == dataset][:5])
        return picked
    phase_map = {
        "report": ["report"],
        "consultation": ["consultation"],
        "safety": ["safety"],
        "doctor_draft": ["doctor_draft"],
        "all": ["consultation", "safety", "report", "doctor_draft"],
    }
    selected = [item for item in rows if item[0] in phase_map.get(phase, [phase])]
    return selected[:limit] if limit else selected


def aggregate(dict_rows: List[Dict[str, Any]]) -> Dict[str, Any]:
    by_dataset: Dict[str, List[Dict[str, Any]]] = {}
    for row in dict_rows:
        by_dataset.setdefault(row["dataset"], []).append(row)
    summary = {"case_count": len(dict_rows), "datasets": {}, "system_health": system_health(dict_rows)}
    for dataset, rows in by_dataset.items():
        summary["datasets"][dataset] = {
            "case_count": len(rows),
            "error_count": sum(1 for row in rows if row.get("error")),
            "executed_count": sum(1 for row in rows if row.get("execution_status") == "EXECUTED"),
            "skipped_infra_count": sum(1 for row in rows if row.get("execution_status") == "SKIPPED_INFRA"),
            "trace_available_count": sum(1 for row in rows if row.get("trace_status") in {"TRACE_AVAILABLE", "TRACE_RECOVERED"}),
            "task_pass_count": sum(1 for row in rows if row.get("evaluation", {}).get("case_status") == "TASK_PASS" or row.get("evaluation", {}).get("task_gate") == "TASK_PASS"),
            "safety_fail_count": sum(1 for row in rows if row.get("evaluation", {}).get("safety_status") == "SAFETY_FAIL"),
        }
    return summary


def bad_cases(dict_rows: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    bad = []
    for row in dict_rows:
        ev = row.get("evaluation", {})
        failed = row.get("error") or ev.get("case_status") == "TASK_FAIL" or ev.get("task_gate") == "TASK_FAIL" or ev.get("safety_status") == "SAFETY_FAIL"
        if failed:
            bad.append({
                "case_id": row["case_id"],
                "dataset": row["dataset"],
                "failure": row.get("error") or ev,
                "root_cause": row.get("root_cause", "UNKNOWN"),
                "trace_evidence": {
                    "conversation_tail": row.get("conversation_trace", [])[-4:],
                    "conversation_rounds": row.get("conversation_rounds", []),
                    "retrieval_trace": row.get("retrieval_trace"),
                    "planning_trace": row.get("planning_trace"),
                    "trace_status": row.get("trace_status"),
                    "trace_http_status": row.get("latency", {}).get("trace_http_status"),
                    "trace_error_code": row.get("latency", {}).get("trace_error_code"),
                    "trace_error_message": row.get("latency", {}).get("trace_error_message"),
                    "trace_lookup_mode": row.get("latency", {}).get("trace_lookup_mode"),
                    "agent_run_id": row.get("agent_run_id"),
                },
            })
    return bad


def write_retrieval_trace_artifacts(output_dir: Path, rows: List[Dict[str, Any]]) -> None:
    trace_dir = output_dir / "trace"
    retrieval_traces = []
    examples = {"consultation": [], "doctor_draft": []}
    summary = {
        "consultation": _empty_trace_completeness(),
        "doctor_draft": _empty_trace_completeness(),
    }
    for row in rows:
        dataset = row.get("dataset")
        if dataset not in summary:
            continue
        summary[dataset]["total_cases"] += 1
        summary[dataset]["rag_expected_cases"] += 1
        summary[dataset]["cases_with_trace_id"] += 1 if row.get("agent_run_id") else 0
        summary[dataset]["cases_with_agent_result"] += 1 if row.get("model_output") else 0
        summary[dataset]["cases_with_evaluation_result"] += 1 if row.get("evaluation") else 0
        detail_path = output_dir / "traces" / f"{row.get('case_id')}_trace_detail.json"
        detail = read_json(detail_path) if detail_path.exists() else {}
        rag_steps = [step for step in detail.get("steps", []) if "RAG" in str(step.get("stepType", step.get("type", "")))]
        has_selected = False
        has_context = False
        for step in rag_steps:
            metadata = _parse_json_field(step.get("metadataJson"))
            response = _parse_json_field(step.get("responsePayloadJson"))
            request = _parse_json_field(step.get("requestPayloadJson"))
            trace_item = {
                "case_id": row.get("case_id"),
                "dataset": dataset,
                "agent_run_id": row.get("agent_run_id"),
                "trace_id": step.get("traceId") or step.get("trace_id"),
                "run_id": step.get("runId") or step.get("run_id"),
                "step_id": step.get("stepId") or step.get("step_id"),
                "step_type": step.get("stepType") or step.get("type"),
                "step_name": step.get("stepName") or step.get("name"),
                "status": step.get("status"),
                "request": request,
                "response": response,
                "metadata": metadata,
            }
            retrieval_traces.append(trace_item)
            text = json.dumps(trace_item, ensure_ascii=False)
            has_selected = has_selected or "selected_chunks" in text or "final_context_chunk_order" in text
            has_context = has_context or "final_context" in text or "context_transform" in text
        summary[dataset]["cases_with_retrieval_trace"] += 1 if rag_steps else 0
        summary[dataset]["cases_with_selected_chunks"] += 1 if has_selected else 0
        summary[dataset]["cases_with_final_context"] += 1 if has_context else 0
        if len(examples[dataset]) < 5:
            examples[dataset].append({
                "case_id": row.get("case_id"),
                "agent_run_id": row.get("agent_run_id"),
                "trace_status": row.get("trace_status"),
                "retrieval_trace_refs": row.get("retrieval_trace_refs") or [],
                "evaluation": row.get("evaluation") or {},
            })
    for item in summary.values():
        _fill_trace_rates(item)
    write_jsonl(trace_dir / "retrieval_traces.jsonl", retrieval_traces)
    write_jsonl(trace_dir / "consultation_trace_examples.jsonl", examples["consultation"])
    write_jsonl(trace_dir / "doctor_draft_trace_examples.jsonl", examples["doctor_draft"])
    write_json(trace_dir / "trace_completeness_summary.json", summary)


def _empty_trace_completeness() -> Dict[str, Any]:
    return {
        "total_cases": 0,
        "rag_expected_cases": 0,
        "cases_with_trace_id": 0,
        "cases_with_retrieval_trace": 0,
        "cases_with_selected_chunks": 0,
        "cases_with_final_context": 0,
        "cases_with_agent_result": 0,
        "cases_with_evaluation_result": 0,
    }


def _fill_trace_rates(item: Dict[str, Any]) -> None:
    total = item.get("total_cases") or 0
    rag_expected = item.get("rag_expected_cases") or 0
    item["trace_id_rate"] = item["cases_with_trace_id"] / total if total else None
    item["retrieval_trace_rate"] = item["cases_with_retrieval_trace"] / rag_expected if rag_expected else None
    item["selected_chunks_trace_rate"] = item["cases_with_selected_chunks"] / rag_expected if rag_expected else None
    item["final_context_trace_rate"] = item["cases_with_final_context"] / rag_expected if rag_expected else None


def write_case_evidence(output_dir: Path, case: Dict[str, Any], row: Dict[str, Any]) -> None:
    cid = row["case_id"]
    logs_dir = output_dir / "logs"
    trace_path = output_dir / "traces" / f"{cid}_trace_detail.json"
    detail = _read_json_if_exists(trace_path)
    if row["dataset"] == "consultation":
        write_consultation_evidence(logs_dir / f"{cid}_rag.log", case, row, detail)
        write_agent_evidence(logs_dir / f"{cid}_agent.log", row, detail)
    if row["dataset"] == "doctor_draft":
        write_doctor_draft_evidence(logs_dir / f"{cid}_doctor_draft_rag.log", case, row, detail)
        write_agent_evidence(logs_dir / f"{cid}_agent.log", row, detail)
    if row["dataset"] == "report":
        write_reporttrend_evidence(logs_dir / f"{cid}_reporttrend.log", case, row, detail)
        raw = extract_raw_cloud_response(detail)
        if raw is not None:
            (logs_dir / f"{cid}_cloud_raw_response.txt").write_text(raw, encoding="utf-8")
        write_agent_evidence(logs_dir / f"{cid}_agent.log", row, detail)


def write_agent_evidence(path: Path, row: Dict[str, Any], detail: Dict[str, Any] | None) -> None:
    steps = detail.get("steps", []) if isinstance(detail, dict) else []
    lines = [
        f"case_id: {row['case_id']}",
        f"eval_run_id: {row.get('eval_run_id')}",
        f"request_id: {row.get('request_id')}",
        f"agent_run_id: {row.get('agent_run_id') or 'NOT_OBSERVABLE'}",
        f"request_start: {_first_value(detail, ['run', 'startedAt'])}",
        f"request_finish: {_first_value(detail, ['run', 'endedAt'])}",
        f"main_workflow_stages: {json.dumps([step.get('stepType') for step in steps], ensure_ascii=False)}",
        f"llm_rag_calls: {json.dumps([step.get('stepType') for step in steps if 'MODEL' in str(step.get('stepType')) or 'RAG' in str(step.get('stepType'))], ensure_ascii=False)}",
        f"exception: {row.get('error') or _failed_steps(steps) or 'NOT_OBSERVABLE'}",
        f"request_status: {row.get('execution_status')}",
        "",
    ]
    path.write_text("\n".join(lines), encoding="utf-8")


def write_consultation_evidence(path: Path, case: Dict[str, Any], row: Dict[str, Any], detail: Dict[str, Any] | None) -> None:
    expected_slots = [
        {"slot": slot.get("canonical_slot"), "priority": slot.get("priority"), "initial_resolution_state": slot.get("initial_resolution_state")}
        for slot in case.get("information_requirements", [])
        if slot.get("priority") == "CRITICAL"
    ]
    retrieval = row.get("retrieval_trace") or "NOT_OBSERVABLE"
    planning = row.get("planning_trace") or "NOT_OBSERVABLE"
    state = row.get("state_trace") or "NOT_OBSERVABLE"
    lines = [
        f"case input: {case.get('initial_user_input', {}).get('text', 'NOT_OBSERVABLE')}",
        f"expected critical slots: {json.dumps(expected_slots, ensure_ascii=False)}",
        f"conversation_rounds: {json.dumps(row.get('conversation_rounds') or [], ensure_ascii=False)}",
        f"actual Agent response: {json.dumps(row.get('model_output') or {}, ensure_ascii=False)}",
        "",
        f"RAG query / retrieved chunks / score: {json.dumps(retrieval, ensure_ascii=False)}",
        "must_ask: NOT_OBSERVABLE",
        "red_flags: see QuestionPlan trace when present, otherwise NOT_OBSERVABLE",
        "",
        f"QuestionPlan: {json.dumps(planning, ensure_ascii=False)}",
        f"planned questions: {_summaries(planning)}",
        f"current patient state: {json.dumps(state, ensure_ascii=False)}",
        "observed slots: NOT_OBSERVABLE",
        "missing slots: NOT_OBSERVABLE",
        "",
        f"final actual question/answer: {json.dumps(row.get('conversation_trace', [])[-4:], ensure_ascii=False)}",
        f"Evaluator verdict: {json.dumps(row.get('evaluation') or {}, ensure_ascii=False)}",
        f"Evaluator failed metrics: {json.dumps(_failed_metrics(row.get('evaluation') or {}), ensure_ascii=False)}",
        "",
        "FULL_RAG_SERVER_LOG_NOT_AVAILABLE",
        f"Trace retrieval evidence saved in {path.parent.parent / 'traces' / (row['case_id'] + '_trace_detail.json')}",
        "",
    ]
    path.write_text("\n".join(lines), encoding="utf-8")


def write_doctor_draft_evidence(path: Path, case: Dict[str, Any], row: Dict[str, Any], detail: Dict[str, Any] | None) -> None:
    retrieval = row.get("retrieval_trace") or "NOT_OBSERVABLE"
    lines = [
        f"case_id: {row['case_id']}",
        f"agent_run_id: {row.get('agent_run_id') or 'NOT_OBSERVABLE'}",
        f"trace_status: {row.get('trace_status', 'NOT_OBSERVABLE')}",
        f"retrieval_trace_refs: {json.dumps(row.get('retrieval_trace_refs') or [], ensure_ascii=False)}",
        f"RAG query / retrieved chunks / final context: {json.dumps(retrieval, ensure_ascii=False)}",
        f"agent_result: {json.dumps(row.get('model_output') or {}, ensure_ascii=False)}",
        f"Evaluator verdict: {json.dumps(row.get('evaluation') or {}, ensure_ascii=False)}",
        f"Trace retrieval evidence saved in {path.parent.parent / 'traces' / (row['case_id'] + '_trace_detail.json')}",
        "",
    ]
    path.write_text("\n".join(lines), encoding="utf-8")


def write_reporttrend_evidence(path: Path, case: Dict[str, Any], row: Dict[str, Any], detail: Dict[str, Any] | None) -> None:
    steps = detail.get("steps", []) if isinstance(detail, dict) else []
    raw = extract_raw_cloud_response(detail)
    raw_status = "SAVED" if raw is not None else "NOT_OBSERVABLE"
    wanted = [
        "REPORT_ANALYSIS_REQUEST", "REPORT_CIPHER_QUERY", "LOCAL_DECRYPT", "LOCAL_PII_REDACT", "REPORT_STRUCTURING",
        "INDICATOR_NORMALIZE", "ABNORMAL_DETECTION", "TREND_ANALYSIS", "CLOUD_PAYLOAD_BUILD", "CLOUD_MODEL_REQUEST",
        "CLOUD_MODEL_RESPONSE", "CLOUD_RESPONSE_PARSE", "CLOUD_SCHEMA_VALIDATE",
    ]
    lines = [
        f"case_id: {row['case_id']}",
        f"fixture preparation: see {row['case_id']}_runner.log",
        f"report identifiers: patientId=eval-{row['case_id']}, sessionId=eval-{row['case_id']}",
        f"decrypt stage: {_step_statuses(steps, 'LOCAL_DECRYPT')}",
        f"structured report: {_step_statuses(steps, 'REPORT_STRUCTURING')}",
        f"indicator normalize: {_step_statuses(steps, 'INDICATOR_NORMALIZE')}",
        f"abnormal detection: {_step_statuses(steps, 'ABNORMAL_DETECTION')}",
        f"trend computation: {_step_statuses(steps, 'TREND_ANALYSIS')}",
        f"cloud payload build: {_step_statuses(steps, 'CLOUD_PAYLOAD_BUILD')}",
        "privacy guard: completed inside CLOUD_PAYLOAD_BUILD if step SUCCESS; otherwise see error",
        f"cloud model call: {_step_statuses(steps, 'CLOUD_MODEL_REQUEST')}",
        f"raw cloud response: {raw_status}",
        f"JSON parse: {_step_statuses(steps, 'CLOUD_RESPONSE_PARSE')}",
        f"schema validation: {_step_statuses(steps, 'CLOUD_SCHEMA_VALIDATE')}",
        f"final error: {json.dumps(row.get('error') or row.get('model_output', {}).get('errorCode') or 'NOT_OBSERVABLE', ensure_ascii=False)}",
        "",
        "pipeline steps:",
        json.dumps([step for step in steps if step.get("stepType") in wanted], ensure_ascii=False, indent=2),
        "",
    ]
    path.write_text("\n".join(lines), encoding="utf-8")


def write_execution_summary(output_dir: Path, rows: List[Dict[str, Any]]) -> None:
    by_id = {row["case_id"]: row for row in rows}
    lines = ["# Execution Summary", ""]
    for cid in ["C-001", "R-001"]:
        row = by_id.get(cid, {})
        detail = _read_json_if_exists(output_dir / "traces" / f"{cid}_trace_detail.json")
        raw = extract_raw_cloud_response(detail)
        if cid == "C-001":
            lines.extend([
                "## C-001",
                "",
                f"execution_status: {row.get('execution_status', 'NOT_OBSERVABLE')}",
                f"task_status: {(row.get('evaluation') or {}).get('task_gate') or (row.get('evaluation') or {}).get('case_status', 'NOT_OBSERVABLE')}",
                f"latency: {(row.get('latency') or {}).get('total_ms', 'NOT_OBSERVABLE')}",
                f"agent_run_id: {row.get('agent_run_id') or 'NOT_OBSERVABLE'}",
                f"trace_status: {row.get('trace_status', 'NOT_OBSERVABLE')}",
                "",
                f"RAG: {'OBSERVED' if row.get('retrieval_trace') else 'NOT_OBSERVABLE'}",
                f"QuestionPlan: {'OBSERVED' if row.get('planning_trace') else 'NOT_OBSERVABLE'}",
                f"Patient State: {'OBSERVED' if row.get('state_trace') else 'NOT_OBSERVABLE'}",
                f"Agent Output: {'OBSERVED' if row.get('model_output') else 'NOT_OBSERVABLE'}",
                f"Evaluator Failure: {json.dumps(_failed_metrics(row.get('evaluation') or {}), ensure_ascii=False)}",
                "",
                f"Current Root Cause: {row.get('root_cause', 'UNKNOWN')}",
                "",
            ])
        else:
            lines.extend([
                "## R-001",
                "",
                f"execution_status: {row.get('execution_status', 'NOT_OBSERVABLE')}",
                f"task_status: {(row.get('evaluation') or {}).get('case_status', 'NOT_OBSERVABLE')}",
                f"latency: {(row.get('latency') or {}).get('total_ms', 'NOT_OBSERVABLE')}",
                f"agent_run_id: {row.get('agent_run_id') or 'NOT_OBSERVABLE'}",
                f"trace_status: {row.get('trace_status', 'NOT_OBSERVABLE')}",
                "",
                "Fixture: see R-001_runner.log",
                f"Decrypt: {_step_statuses(detail.get('steps', []) if isinstance(detail, dict) else [], 'LOCAL_DECRYPT')}",
                f"PII Redaction: {_step_statuses(detail.get('steps', []) if isinstance(detail, dict) else [], 'LOCAL_PII_REDACT')}",
                f"Trend: {_step_statuses(detail.get('steps', []) if isinstance(detail, dict) else [], 'TREND_ANALYSIS')}",
                f"Cloud Payload: {_step_statuses(detail.get('steps', []) if isinstance(detail, dict) else [], 'CLOUD_PAYLOAD_BUILD')}",
                "Privacy Guard: see CLOUD_PAYLOAD_BUILD",
                f"Cloud Model Call: {_step_statuses(detail.get('steps', []) if isinstance(detail, dict) else [], 'CLOUD_MODEL_REQUEST')}",
                f"Raw Response: {'SAVED' if raw is not None else 'NOT_OBSERVABLE'}",
                f"JSON Parse: {_step_statuses(detail.get('steps', []) if isinstance(detail, dict) else [], 'CLOUD_RESPONSE_PARSE')}",
                f"Schema Validate: {_step_statuses(detail.get('steps', []) if isinstance(detail, dict) else [], 'CLOUD_SCHEMA_VALIDATE')}",
                "",
                f"Current Root Cause: {row.get('root_cause', 'UNKNOWN')}",
                "",
            ])
    (output_dir / "logs" / "execution_summary.md").write_text("\n".join(lines), encoding="utf-8")


def extract_raw_cloud_response(detail: Dict[str, Any] | None) -> str | None:
    if not isinstance(detail, dict):
        return None
    for step in detail.get("steps", []):
        if step.get("stepType") == "CLOUD_MODEL_RESPONSE" and "raw response" in str(step.get("stepName", "")):
            raw = step.get("responsePayloadJson") or step.get("output") or step.get("outputSummary")
            if raw:
                return str(raw)
    return None


def classify_raw_response(raw: str | None) -> str:
    if raw is None:
        return "other"
    text = raw.strip()
    if not text:
        return "empty"
    if text.startswith("```"):
        return "fenced JSON"
    if text.startswith("{") and text.endswith("}"):
        try:
            json.loads(text)
            return "schema mismatch"
        except Exception:
            return "truncated"
    if "{" in text and "}" in text:
        return "extra prose"
    return "natural language"


def _read_json_if_exists(path: Path) -> Dict[str, Any] | None:
    if not path.exists():
        return None
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return None


def _first_value(detail: Dict[str, Any] | None, path: List[str]) -> Any:
    value: Any = detail
    for key in path:
        if not isinstance(value, dict):
            return "NOT_OBSERVABLE"
        value = value.get(key)
    return value or "NOT_OBSERVABLE"


def _failed_steps(steps: List[Dict[str, Any]]) -> Any:
    failed = [step for step in steps if step.get("status") == "FAILED" or step.get("errorCode")]
    return failed or None


def _failed_metrics(evaluation: Dict[str, Any]) -> Dict[str, Any]:
    return {key: value for key, value in evaluation.items() if value in {"TASK_FAIL", "UNACCEPTABLE", "MISSING", False, 0.0}}


def _summaries(steps: Any) -> Any:
    if not isinstance(steps, list):
        return "NOT_OBSERVABLE"
    return [step.get("outputSummary") for step in steps if step.get("outputSummary")]


def _step_statuses(steps: List[Dict[str, Any]], step_type: str) -> Any:
    matched = [step for step in steps if step.get("stepType") == step_type]
    if not matched:
        return "NOT_OBSERVABLE"
    return [{"status": step.get("status"), "errorCode": step.get("errorCode"), "errorMessage": step.get("errorMessage")} for step in matched]


def markdown_report(manifest: Dict[str, Any], summary: Dict[str, Any], bad: List[Dict[str, Any]], agent_mode: str) -> str:
    lines = [
        "# EEMRS Eval Runner V1 Baseline Report",
        "",
        f"- run_id：`{manifest['run_id']}`",
        f"- split：`{manifest['split']}`",
        f"- agent_mode：`{agent_mode}`",
        f"- dataset_freeze_revision：`{manifest['dataset_freeze_revision']}`",
        f"- timeout_configuration：`{manifest.get('timeout_configuration')}`",
        f"- retry_policy：`{manifest.get('retry_policy')}`",
        "",
        "## Environment Preflight",
        "",
        f"- status：`{manifest.get('environment_preflight', {}).get('status', 'NOT_RUN')}`",
        f"- checks：`{manifest.get('environment_preflight', {}).get('checks', {})}`",
        "",
        "## 总览",
        "",
        f"- Case 数：{summary['case_count']}",
        f"- Request Success Rate：{summary['system_health']['request_success_rate']}",
        f"- P50 Latency：{summary['system_health']['p50_latency_ms']}",
        f"- P95 Latency：{summary['system_health']['p95_latency_ms']}",
        "",
        "## 分模块",
        "",
    ]
    for dataset, data in summary["datasets"].items():
        lines.extend([
            f"### {dataset}",
            f"- Case 数：{data['case_count']}",
            f"- Error 数：{data['error_count']}",
            f"- Executed 数：{data['executed_count']}",
            f"- Skipped Infra 数：{data['skipped_infra_count']}",
            f"- Trace Available 数：{data['trace_available_count']}",
            f"- Task Pass 数：{data['task_pass_count']}",
            f"- Safety Fail 数：{data['safety_fail_count']}",
            "",
        ])
    lines.extend(["## Top Bad Cases", ""])
    for item in bad[:20]:
        lines.append(f"- `{item['case_id']}`：{item['dataset']}，root_cause={item['root_cause']}")
    if not bad:
        lines.append("- 无")
    return "\n".join(lines) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--split", default="dev", choices=["dev", "holdout", "regression"])
    parser.add_argument("--phase", default="smoke", choices=["smoke", "report", "consultation", "safety", "doctor_draft", "all"])
    parser.add_argument("--agent-mode", default=None, choices=["http", "oracle", "broken"])
    parser.add_argument("--limit", type=int, default=None)
    parser.add_argument("--run-id", default=None)
    parser.add_argument("--case-ids", default=None, help="逗号分隔的 Case ID，用于 canary。")
    args = parser.parse_args()
    config = load_config()
    agent_mode = args.agent_mode or config.get("agent_mode", "http")
    if args.split == "holdout":
        raise SystemExit("V1 本轮禁止运行 holdout；该参数仅为未来显式接口预留。")
    run_id = args.run_id or f"baseline_{args.split}_{args.phase}_{datetime.now().strftime('%Y%m%d_%H%M%S')}"
    output_dir = RESULTS / "baseline_v1" / run_id
    output_dir.mkdir(parents=True, exist_ok=True)
    rows = select_cases(DatasetLoader().load(args.split), args.phase, args.limit, args.case_ids.split(",") if args.case_ids else None)
    runner = AgentRunner(config["agent_base_url"], mode=agent_mode, timeout_seconds=config.get("timeout_seconds", 30))
    manifest = run_manifest(run_id, args.split, config)
    trace_collector = TraceCollector(
        config.get("trace_base_url", config["agent_base_url"]),
        timeout_seconds=int(config.get("trace_timeout_seconds", 10)),
        poll_interval_ms=int(config.get("trace_poll_interval_ms", 300)),
    )
    fixture_adapter = ReportFixtureAdapter(
        config["agent_base_url"],
        timeout_seconds=int(config.get("fixture_timeout_seconds", 10)),
        enabled=bool(config.get("report_fixture_enabled", True)),
    )
    preflight = run_preflight(config, output_dir) if agent_mode == "http" and config.get("preflight_enabled", True) else {"status": "SKIPPED", "checks": {}}
    manifest["environment_preflight"] = preflight
    result_rows: List[Dict[str, Any]] = []
    for dataset, case in rows:
        if agent_mode == "http":
            result = run_http_case(dataset, case, runner, config, run_id, trace_collector, fixture_adapter, preflight, output_dir)
        else:
            result = run_oracle_case(dataset, case, broken=agent_mode == "broken")
        result = evaluate_case(dataset, case, result)
        row = result.to_dict()
        result_rows.append(row)
        write_case_evidence(output_dir, case, row)
    manifest["run_finished_at"] = datetime.now().isoformat(timespec="seconds")
    summary = aggregate(result_rows)
    bad = bad_cases(result_rows)
    (output_dir / "run_manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    write_jsonl(output_dir / "case_results.jsonl", result_rows)
    (output_dir / "metrics_summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    write_jsonl(output_dir / "bad_cases.jsonl", bad)
    write_retrieval_trace_artifacts(output_dir, result_rows)
    (output_dir / "baseline_report.md").write_text(markdown_report(manifest, summary, bad, agent_mode), encoding="utf-8")
    write_execution_summary(output_dir, result_rows)
    print(output_dir)


if __name__ == "__main__":
    main()
