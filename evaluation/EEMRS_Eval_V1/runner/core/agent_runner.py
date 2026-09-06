import json
import socket
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from typing import Any, Dict


class AgentRequestError(Exception):
    def __init__(self, error_type: str, message: str, status_code: int | None = None, response_body: str | None = None):
        super().__init__(message)
        self.error_type = error_type
        self.status_code = status_code
        self.response_body = response_body


@dataclass
class Correlation:
    eval_run_id: str
    case_id: str
    request_id: str
    agent_run_id: str | None = None


class AgentRunner:
    def __init__(self, base_url: str, mode: str = "http", timeout_seconds: int = 30):
        self.base_url = base_url.rstrip("/")
        self.mode = mode
        self.timeout_seconds = timeout_seconds

    def post(self, path: str, payload: Dict[str, Any], timeout_seconds: int | None = None, correlation: Correlation | None = None) -> Dict[str, Any]:
        start = time.time()
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        headers = {
            "Content-Type": "application/json",
            "X-EEMRS-Eval": "true",
        }
        if correlation:
            headers.update({
                "X-EEMRS-Eval-Run-Id": correlation.eval_run_id,
                "X-EEMRS-Eval-Case-Id": correlation.case_id,
                "X-Request-Id": correlation.request_id,
            })
            if correlation.agent_run_id:
                headers["X-Agent-Run-Id"] = correlation.agent_run_id
        request = urllib.request.Request(
            f"{self.base_url}{path}",
            data=body,
            headers=headers,
            method="POST",
        )
        try:
            with urllib.request.urlopen(request, timeout=timeout_seconds or self.timeout_seconds) as response:
                raw_text = response.read().decode("utf-8")
                raw = json.loads(raw_text) if raw_text else {}
                response_headers = dict(response.headers.items())
            return {
                "raw": raw,
                "headers": response_headers,
                "http_status": response.status,
                "agent_run_id": response_headers.get("X-Agent-Run-Id"),
                "latency_ms": int((time.time() - start) * 1000),
            }
        except urllib.error.HTTPError as exc:
            response_body = exc.read().decode("utf-8", errors="replace")
            if exc.code == 408:
                error_type = "UPSTREAM_TIMEOUT"
            elif 400 <= exc.code < 500:
                error_type = "HTTP_4XX"
            elif 500 <= exc.code < 600:
                error_type = "HTTP_5XX"
            else:
                error_type = "HTTP_ERROR"
            raise AgentRequestError(error_type, str(exc), exc.code, response_body) from exc
        except urllib.error.URLError as exc:
            reason = exc.reason
            if isinstance(reason, TimeoutError) or "timed out" in str(reason).lower():
                raise AgentRequestError("CLIENT_TIMEOUT", str(exc)) from exc
            if isinstance(reason, ConnectionRefusedError) or "connection refused" in str(reason).lower():
                raise AgentRequestError("CONNECTION_REFUSED", str(exc)) from exc
            raise AgentRequestError("DEPENDENCY_UNAVAILABLE", str(exc)) from exc
        except (TimeoutError, socket.timeout) as exc:
            raise AgentRequestError("CLIENT_TIMEOUT", str(exc)) from exc

    def run_pre_consultation(self, payload: Dict[str, Any], timeout_seconds: int | None = None, correlation: Correlation | None = None) -> Dict[str, Any]:
        return self.post("/api/agent/pre-consultation", payload, timeout_seconds, correlation)

    def run_report_trend(self, payload: Dict[str, Any], timeout_seconds: int | None = None, correlation: Correlation | None = None) -> Dict[str, Any]:
        return self.post("/api/agent/report-trend/analyze", payload, timeout_seconds, correlation)

    def run_doctor_draft(self, payload: Dict[str, Any], timeout_seconds: int | None = None, correlation: Correlation | None = None) -> Dict[str, Any]:
        return self.post("/api/agent/medical-record-drafts/generate", payload, timeout_seconds, correlation)
