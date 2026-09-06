"""Shared schema and validation constants for medical RAG knowledge files."""

from __future__ import annotations


REQUIRED_FIELDS = [
    "doc_id",
    "doc_type",
    "title",
    "version",
    "language",
    "source_type",
    "applicable_population",
    "related_symptoms",
    "related_departments",
    "urgency_level",
    "must_ask",
    "red_flags",
    "triage_rules",
    "forbidden_actions",
    "expected_response_points",
    "doctor_record_fields",
    "chunk_text",
]

ALLOWED_DOC_TYPES = {
    "symptom_inquiry",
    "red_flag",
    "special_population",
    "department_triage",
    "medical_record_template",
}

DIR_DOC_TYPE_MAP = {
    "01_symptom_inquiry": "symptom_inquiry",
    "02_red_flags": "red_flag",
    "03_special_population": "special_population",
    "04_department_triage": "department_triage",
    "05_medical_record_templates": "medical_record_template",
}

MILVUS_OUTPUT_FIELDS = [
    "chunk_id",
    "doc_id",
    "doc_type",
    "title",
    "version",
    "language",
    "source_type",
    "applicable_population",
    "related_symptoms",
    "related_departments",
    "urgency_level",
    "content_json",
    "chunk_text",
]
