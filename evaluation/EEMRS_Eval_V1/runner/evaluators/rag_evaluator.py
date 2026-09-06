from typing import Any, Dict


def evaluate(case: Dict[str, Any], retrieval_trace: Any) -> Dict[str, Any]:
    if not retrieval_trace:
        return {"rag_metrics": "NOT_OBSERVABLE", "context_recall": "NOT_OBSERVABLE", "context_precision": "NOT_OBSERVABLE"}
    return {"rag_metrics": "NOT_IMPLEMENTED_V1"}

