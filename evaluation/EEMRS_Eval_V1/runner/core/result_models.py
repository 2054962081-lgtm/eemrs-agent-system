from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional


NOT_OBSERVABLE = "NOT_OBSERVABLE"
REQUIRES_JUDGE = "REQUIRES_JUDGE"
NOT_IMPLEMENTED_V1 = "NOT_IMPLEMENTED_V1"
EXECUTED = "EXECUTED"
SKIPPED_INFRA = "SKIPPED_INFRA"
REQUEST_FAILED = "REQUEST_FAILED"


@dataclass
class CaseResult:
    case_id: str
    dataset: str
    split: str
    input: Dict[str, Any]
    eval_run_id: str = ""
    request_id: str = ""
    agent_run_id: str = ""
    execution_status: str = EXECUTED
    dependency: Optional[str] = None
    trace_status: str = NOT_OBSERVABLE
    model_output: Dict[str, Any] = field(default_factory=dict)
    conversation_trace: List[Dict[str, Any]] = field(default_factory=list)
    conversation_rounds: List[Dict[str, Any]] = field(default_factory=list)
    retrieval_trace: Any = None
    retrieval_trace_refs: Any = None
    planning_trace: Any = None
    state_trace: Any = None
    safety_trace: Any = None
    latency: Dict[str, Any] = field(default_factory=dict)
    error: Optional[Dict[str, Any]] = None
    evaluation: Dict[str, Any] = field(default_factory=dict)
    root_cause: str = "UNKNOWN"

    def to_dict(self) -> Dict[str, Any]:
        return {
            "case_id": self.case_id,
            "dataset": self.dataset,
            "split": self.split,
            "input": self.input,
            "eval_run_id": self.eval_run_id,
            "request_id": self.request_id,
            "agent_run_id": self.agent_run_id,
            "execution_status": self.execution_status,
            "dependency": self.dependency,
            "trace_status": self.trace_status,
            "model_output": self.model_output,
            "conversation_trace": self.conversation_trace,
            "conversation_rounds": self.conversation_rounds,
            "retrieval_trace": self.retrieval_trace,
            "retrieval_trace_refs": self.retrieval_trace_refs,
            "planning_trace": self.planning_trace,
            "state_trace": self.state_trace,
            "safety_trace": self.safety_trace,
            "latency": self.latency,
            "error": self.error,
            "evaluation": self.evaluation,
            "root_cause": self.root_cause,
        }
