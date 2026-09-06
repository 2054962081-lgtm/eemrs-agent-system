from typing import Any, Dict, List

from evaluators.department_canonicalizer import canonicalize_department, canonicalize_departments


def observed_resolved_slots(case: Dict[str, Any], conversation: List[Dict[str, str]]) -> set[str]:
    joined = "\n".join(item.get("content", "") for item in conversation if item.get("role") in {"assistant", "agent"})
    resolved = set()
    for slot in case["information_requirements"]:
        cues = [slot["canonical_slot"], slot["semantic_intent"], *slot.get("accepted_question_intents", []), *slot.get("acceptable_semantics", [])]
        if any(str(cue) and str(cue) in joined for cue in cues):
            resolved.add(slot["canonical_slot"])
    return resolved


def evaluate(case: Dict[str, Any], conversation: List[Dict[str, str]], output: Dict[str, Any] | None = None) -> Dict[str, Any]:
    output = output or {}
    active_critical = [s for s in case["information_requirements"] if s["priority"] == "CRITICAL" and s.get("initial_resolution_state") == "ACTIVE_UNRESOLVED"]
    resolved = observed_resolved_slots(case, conversation)
    resolved_critical = [s for s in active_critical if s["canonical_slot"] in resolved]
    cirr = len(resolved_critical) / len(active_critical) if active_critical else 1.0
    department = output.get("recommendedDepartment") or output.get("recommended_department")
    canonical_department = canonicalize_department(department)
    product = case["product_ground_truth"]
    preferred_department = canonicalize_department(product["preferred_department"])
    acceptable_departments = canonicalize_departments(product["acceptable_departments"])
    if not department:
        routing = "MISSING"
    elif canonical_department == preferred_department:
        routing = "PREFERRED"
    elif canonical_department in acceptable_departments:
        routing = "ACCEPTABLE"
    else:
        routing = "UNACCEPTABLE"
    task_pass = cirr == 1.0 and routing in {"PREFERRED", "ACCEPTABLE"}
    turn_count = len([x for x in conversation if x.get("role") in {"assistant", "agent"}])
    ideal = product.get("ideal_turn_range", [0, 999])
    acceptable = product.get("acceptable_turn_range", [0, 999])
    return {
        "safety_gate": "NOT_APPLICABLE",
        "task_gate": "TASK_PASS" if task_pass else "TASK_FAIL",
        "critical_information_resolution_rate": cirr,
        "critical_information_accuracy": "REQUIRES_JUDGE",
        "important_information_resolution": "NOT_IMPLEMENTED_V1",
        "redundant_ask_rate": "NOT_IMPLEMENTED_V1",
        "irrelevant_ask_rate": "NOT_IMPLEMENTED_V1",
        "routing": routing,
        "turn_count": turn_count,
        "within_ideal": ideal[0] <= turn_count <= ideal[1],
        "within_acceptable": acceptable[0] <= turn_count <= acceptable[1],
        "product_quality": "PARTIAL_ONLY_TASK_PASS_CASES" if task_pass else "NOT_APPLICABLE",
    }
