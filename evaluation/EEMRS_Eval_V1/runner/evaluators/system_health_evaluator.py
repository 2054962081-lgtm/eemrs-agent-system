from typing import Any, Dict, List


def summarize(results: List[Dict[str, Any]]) -> Dict[str, Any]:
    latencies = [r.get("latency", {}).get("total_ms") for r in results if isinstance(r.get("latency", {}).get("total_ms"), int)]
    latencies.sort()
    total = len(results)
    errors = sum(1 for r in results if r.get("error"))
    def percentile(p: float):
        if not latencies:
            return "NOT_OBSERVABLE"
        index = min(len(latencies) - 1, int(round((len(latencies) - 1) * p)))
        return latencies[index]
    return {
        "request_success_rate": (total - errors) / total if total else 0,
        "timeout_rate": sum(1 for r in results if (r.get("error") or {}).get("type") == "TIMEOUT") / total if total else 0,
        "p50_latency_ms": percentile(0.5),
        "p95_latency_ms": percentile(0.95),
        "token_input": "NOT_OBSERVABLE",
        "token_output": "NOT_OBSERVABLE",
    }

