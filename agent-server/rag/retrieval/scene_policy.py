"""Behavior-preserving scene and doc_type retrieval policy."""

from __future__ import annotations

from collections import OrderedDict

from rag.rag_schema import ALLOWED_DOC_TYPES


DEFAULT_DOC_TYPES = [
    "red_flag",
    "symptom_inquiry",
    "special_population",
    "department_triage",
    "medical_record_template",
]

MEDICAL_RECORD_DOC_TYPES = [
    "medical_record_template",
    "symptom_inquiry",
    "special_population",
    "red_flag",
    "department_triage",
]


def quota_for_scene(scene: str) -> OrderedDict[str, int]:
    quotas = {
        "pre_inquiry": OrderedDict([
            ("red_flag", 2),
            ("symptom_inquiry", 2),
            ("special_population", 2),
            ("department_triage", 1),
            ("medical_record_template", 1),
        ]),
        "deep_inquiry": OrderedDict([
            ("red_flag", 2),
            ("symptom_inquiry", 3),
            ("special_population", 2),
            ("department_triage", 1),
            ("medical_record_template", 2),
        ]),
        "medical_record": OrderedDict([
            ("medical_record_template", 3),
            ("symptom_inquiry", 2),
            ("special_population", 2),
            ("red_flag", 1),
            ("department_triage", 1),
        ]),
    }
    return quotas.get(scene, quotas["pre_inquiry"])


def ordered_doc_types(scene: str, include_doc_types: list[str] | None) -> list[str]:
    preferred = MEDICAL_RECORD_DOC_TYPES if scene == "medical_record" else DEFAULT_DOC_TYPES
    requested = include_doc_types or preferred
    requested_set = [doc_type for doc_type in requested if doc_type in ALLOWED_DOC_TYPES]
    ordered = [doc_type for doc_type in preferred if doc_type in requested_set]
    ordered.extend(doc_type for doc_type in requested_set if doc_type not in ordered)
    return ordered or preferred


def doc_type_limits(scene: str, include_doc_types: list[str] | None, top_k: int) -> OrderedDict[str, int]:
    allowed = set(ordered_doc_types(scene, include_doc_types))
    limits = OrderedDict((doc_type, limit) for doc_type, limit in quota_for_scene(scene).items() if doc_type in allowed)
    for doc_type in allowed:
        limits.setdefault(doc_type, max(1, min(2, top_k)))
    return limits


def retrieval_candidate_limit(doc_type_limit: int, top_k: int) -> int:
    return min(30, max(top_k * 3, doc_type_limit * 4, doc_type_limit))


def selection_doc_type_caps(scene: str, include_doc_types: list[str] | None, top_k: int) -> OrderedDict[str, int]:
    caps = doc_type_limits(scene, include_doc_types, top_k)
    return OrderedDict((doc_type, min(limit, top_k)) for doc_type, limit in caps.items())
