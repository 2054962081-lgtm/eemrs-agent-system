from typing import Any, Dict


def to_payload(case: Dict[str, Any]) -> Dict[str, Any]:
    trace = case["input"].get("consultation_trace", [])
    return {
        "sessionId": f"eval-{case['metadata']['case_id']}",
        "patientId": 0,
        "patientIdNumber": f"eval-{case['metadata']['case_id']}",
        "mode": "deep",
        "consultationConclusion": case["input"].get("consultation_summary", ""),
        "history": [{"role": item["role"], "content": item["content"]} for item in trace],
    }

