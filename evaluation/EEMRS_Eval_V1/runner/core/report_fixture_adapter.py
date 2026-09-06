import json
import urllib.error
import urllib.request
from typing import Any, Dict, List


class ReportFixtureError(Exception):
    pass


class ReportFixtureAdapter:
    def __init__(self, base_url: str, timeout_seconds: int = 10, enabled: bool = True):
        self.base_url = base_url.rstrip("/")
        self.timeout_seconds = timeout_seconds
        self.enabled = enabled

    def prepare(self, case: Dict[str, Any], eval_run_id: str) -> Dict[str, Any] | None:
        if not self.enabled:
            return None
        payload = self._fixture_payload(case, eval_run_id)
        return self._post("/api/eval/fixtures/report/prepare", payload)

    def cleanup(self, case_id: str, eval_run_id: str) -> Dict[str, Any] | None:
        if not self.enabled:
            return None
        return self._post("/api/eval/fixtures/report/cleanup", {"caseId": case_id, "evalRunId": eval_run_id})

    def _post(self, path: str, payload: Dict[str, Any]) -> Dict[str, Any]:
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        request = urllib.request.Request(
            f"{self.base_url}{path}",
            data=body,
            headers={"Content-Type": "application/json", "X-EEMRS-Eval": "true"},
            method="POST",
        )
        try:
            with urllib.request.urlopen(request, timeout=self.timeout_seconds) as response:
                raw = json.loads(response.read().decode("utf-8"))
            data = raw.get("data", raw)
            if isinstance(raw, dict) and raw.get("success") is False:
                raise ReportFixtureError(str(raw))
            return data
        except urllib.error.HTTPError as exc:
            raise ReportFixtureError(f"fixture HTTP {exc.code}: {exc.read().decode('utf-8', errors='replace')}") from exc
        except Exception as exc:
            raise ReportFixtureError(str(exc)) from exc

    def _fixture_payload(self, case: Dict[str, Any], eval_run_id: str) -> Dict[str, Any]:
        case_id = case["metadata"]["case_id"]
        bundle = case.get("expected_clean_bundle") or case.get("scenario", {}).get("report_bundle", [])
        return {
            "evalRunId": eval_run_id,
            "caseId": case_id,
            "patientId": f"eval-{case_id}",
            "sessionId": f"eval-{case_id}",
            "reportType": "LAB",
            "records": [_record(item) for item in bundle],
        }


def _record(item: Dict[str, Any]) -> Dict[str, Any]:
    reference = item.get("reference_range")
    return {
        "recordId": item.get("record_id") or f"{item.get('indicator')}-{item.get('date')}",
        "date": item.get("date"),
        "indicator": item.get("indicator"),
        "value": item.get("value"),
        "unit": item.get("unit"),
        "referenceLow": reference.get("lower") if isinstance(reference, dict) else None,
        "referenceHigh": reference.get("upper") if isinstance(reference, dict) else None,
        "referenceRangeEvaluable": item.get("reference_range_evaluable", False),
    }
