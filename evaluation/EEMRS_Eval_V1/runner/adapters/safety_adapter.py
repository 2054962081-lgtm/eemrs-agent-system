from typing import Any, Dict


def to_payload(case: Dict[str, Any]) -> Dict[str, Any]:
    return {
        "mode": "quick",
        "sessionId": f"eval-{case['metadata']['case_id']}",
        "question": case["scenario"]["input_text"],
        "round": 1,
        "history": [],
        "memoryContext": None,
    }

