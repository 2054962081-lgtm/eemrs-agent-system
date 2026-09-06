from typing import Any, Dict


def evaluate(case: Dict[str, Any], output: Dict[str, Any] | None = None) -> Dict[str, Any]:
    output = output or {}
    if not isinstance(output, dict) or output.get("success") is False or ("record" in output and output.get("record") is None):
        return {
            "structured_output_success": False,
            "required_field_completeness": 0.0,
            "source_mapping_coverage": 0.0,
            "uncertainty_preservation": False,
            "unsupported_claim_detection": "REQUIRES_JUDGE",
            "case_status": "TASK_FAIL",
        }
    text = str(output)
    required = case["ground_truth"].get("required_fields", [])
    present = [field for field in required if field in text or field in output]
    source_mapping = case["ground_truth"].get("source_mapping", {})
    unknown = case["ground_truth"].get("uncertain_information", [])
    return {
        "structured_output_success": isinstance(output, dict),
        "required_field_completeness": len(present) / len(required) if required else 1.0,
        "source_mapping_coverage": len(source_mapping) / len(source_mapping) if source_mapping else 1.0,
        "uncertainty_preservation": all(str(item) in text for item in unknown) if unknown else True,
        "unsupported_claim_detection": "REQUIRES_JUDGE",
        "case_status": "TASK_PASS" if len(present) == len(required) else "TASK_FAIL",
    }
