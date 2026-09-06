import json
import socket
import time
import urllib.error
import urllib.request
from datetime import datetime
from pathlib import Path
from typing import Any, Dict, List


def run_preflight(config: Dict[str, Any], output_dir: Path | None = None) -> Dict[str, Any]:
    checks = {
        "agent_server": _http_get(config["agent_base_url"].rstrip("/") + "/api/agent/health", timeout=3),
        "rag_service": _http_get(
            config.get("rag_base_url", "http://127.0.0.1:18080").rstrip()
            + config.get("rag_health_path", "/memory/health"),
            timeout=3,
            require_success_true=True,
        ),
        "report_fixture": _http_get(config["agent_base_url"].rstrip("/") + "/api/eval/fixtures/report/health", timeout=3),
    }
    agent_up = checks["agent_server"]["available"]
    all_up = all(check["available"] for check in checks.values())
    result = {
        "timestamp": datetime.now().isoformat(timespec="seconds"),
        "status": "PASS" if all_up else ("DEGRADED" if agent_up else "FAIL"),
        "checks": checks,
        "dependency_map": config.get("dependency_map", {}),
    }
    if output_dir:
        (output_dir / "system_health.json").write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    return result


def unavailable_dependency(dataset: str, preflight: Dict[str, Any], config: Dict[str, Any]) -> str | None:
    for dependency in config.get("dependency_map", {}).get(dataset, ["agent_server"]):
        check = preflight.get("checks", {}).get(dependency)
        if check and not check.get("available"):
            return dependency
    return None


def _http_get(url: str, timeout: int, require_success_true: bool = False) -> Dict[str, Any]:
    started = time.time()
    try:
        with urllib.request.urlopen(url, timeout=timeout) as response:
            body = response.read().decode("utf-8", errors="replace")
        success_true = _success_true(body)
        if require_success_true and not success_true:
            return _down(url, started, "HEALTH_NOT_READY", "HTTP 200 but success=true was not present")
        return {
            "url": url,
            "available": True,
            "status": "UP",
            "http_status": response.status,
            "success_true": success_true,
            "latency_ms": int((time.time() - started) * 1000),
            "body_preview": body[:300],
        }
    except urllib.error.HTTPError as exc:
        return _down(url, started, "HTTP_" + str(exc.code), str(exc))
    except urllib.error.URLError as exc:
        reason = exc.reason
        if isinstance(reason, ConnectionRefusedError) or "connection refused" in str(reason).lower():
            status = "CONNECTION_REFUSED"
        elif isinstance(reason, TimeoutError) or "timed out" in str(reason).lower():
            status = "CLIENT_TIMEOUT"
        else:
            status = "DEPENDENCY_UNAVAILABLE"
        return _down(url, started, status, str(exc))
    except (TimeoutError, socket.timeout) as exc:
        return _down(url, started, "CLIENT_TIMEOUT", str(exc))
    except Exception as exc:
        return _down(url, started, "DEPENDENCY_UNAVAILABLE", str(exc))


def _down(url: str, started: float, status: str, message: str) -> Dict[str, Any]:
    return {
        "url": url,
        "available": False,
        "status": status,
        "latency_ms": int((time.time() - started) * 1000),
        "error": message,
    }


def _success_true(body: str) -> bool:
    try:
        parsed = json.loads(body)
        return parsed.get("success") is True
    except Exception:
        return False
