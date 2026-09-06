import json
import time
import urllib.error
import urllib.parse
import urllib.request
from typing import Any, Dict


class TraceCollector:
    def __init__(self, base_url: str, timeout_seconds: int = 10, poll_interval_ms: int = 300):
        self.base_url = base_url.rstrip("/")
        self.timeout_seconds = timeout_seconds
        self.poll_interval_ms = poll_interval_ms

    def get_detail(self, run_id: str) -> Dict[str, Any]:
        if not run_id:
            return {"status": "NOT_OBSERVABLE", "detail": None}
        url = f"{self.base_url}/api/agent-traces/runs/{run_id}/detail"
        return self._poll_detail(url, "run_id")

    def get_detail_by_request_id(self, request_id: str) -> Dict[str, Any]:
        if not request_id:
            return {"status": "NOT_OBSERVABLE", "detail": None}
        encoded = urllib.parse.quote(request_id, safe="")
        url = f"{self.base_url}/api/agent-traces/lookup/request/{encoded}/detail"
        recovered = self._poll_detail(url, "request_id")
        if recovered["status"] == "TRACE_AVAILABLE":
            recovered["status"] = "TRACE_RECOVERED"
        return recovered

    def _poll_detail(self, url: str, lookup_mode: str = "run_id") -> Dict[str, Any]:
        deadline = time.time() + self.timeout_seconds
        last_status = "TRACE_PENDING"
        last_error = None
        last_result: Dict[str, Any] = {"status": "TRACE_PENDING", "detail": None, "trace_lookup_mode": lookup_mode}
        while time.time() <= deadline:
            fetched = self._fetch(url, lookup_mode)
            if fetched["status"] == "TRACE_AVAILABLE":
                return fetched
            if fetched["status"] not in {"TRACE_PENDING", "TRACE_NOT_FOUND"}:
                return fetched
            last_status = fetched["status"]
            last_error = fetched.get("error")
            last_result = fetched
            time.sleep(self.poll_interval_ms / 1000)
        last_result.update({
            "status": "TRACE_POLL_TIMEOUT" if last_status in {"TRACE_PENDING", "TRACE_NOT_FOUND"} else last_status,
            "detail": None,
            "error": last_error,
            "trace_error_code": "TRACE_POLL_TIMEOUT",
            "trace_error_message": last_error or "Trace polling timed out",
            "trace_lookup_mode": lookup_mode,
        })
        return last_result

    def _fetch(self, url: str, lookup_mode: str = "run_id") -> Dict[str, Any]:
        try:
            with urllib.request.urlopen(url, timeout=self.timeout_seconds) as response:
                raw = json.loads(response.read().decode("utf-8"))
            detail = raw.get("data", raw)
            run = detail.get("run") if isinstance(detail, dict) else None
            if not run:
                return self._error_result("TRACE_RESPONSE_SHAPE_INVALID", lookup_mode, error="Trace detail response missing run")
            return {"status": "TRACE_AVAILABLE", "detail": detail, "trace_lookup_mode": lookup_mode}
        except urllib.error.HTTPError as exc:
            payload = self._http_error_payload(exc)
            message = payload.get("message") or str(exc)
            if exc.code == 404:
                return self._error_result("TRACE_NOT_FOUND", lookup_mode, exc.code, "TRACE_LOOKUP_FAILED", message)
            code = self._classify_http_error(exc.code, message)
            return self._error_result(code, lookup_mode, exc.code, code, message)
        except json.JSONDecodeError as exc:
            return self._error_result("TRACE_RUNNER_PARSE_FAILED", lookup_mode, error=str(exc))
        except Exception as exc:
            return self._error_result("TRACE_LOOKUP_FAILED", lookup_mode, error=str(exc))

    def _http_error_payload(self, exc: urllib.error.HTTPError) -> Dict[str, Any]:
        try:
            body = exc.read().decode("utf-8")
            parsed = json.loads(body)
            return parsed if isinstance(parsed, dict) else {"message": body}
        except Exception:
            return {"message": str(exc)}

    def _classify_http_error(self, http_status: int, message: str) -> str:
        normalized = (message or "").lower()
        if "no static resource" in normalized and "agent-traces" in normalized:
            return "TRACE_CONTROLLER_FAILED"
        if 500 <= http_status:
            return "TRACE_CONTROLLER_FAILED"
        return "TRACE_LOOKUP_FAILED"

    def _error_result(
        self,
        status: str,
        lookup_mode: str,
        http_status: int | None = None,
        error_code: str | None = None,
        error: str | None = None,
    ) -> Dict[str, Any]:
        result = {
            "status": status,
            "detail": None,
            "error": error,
            "trace_error_code": error_code or status,
            "trace_error_message": error,
            "trace_lookup_mode": lookup_mode,
        }
        if http_status is not None:
            result["trace_http_status"] = http_status
        return result
