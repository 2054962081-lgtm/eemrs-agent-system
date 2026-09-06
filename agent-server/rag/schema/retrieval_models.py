"""Retrieval request and response models for /rag/retrieve."""

from __future__ import annotations

from typing import Any

from pydantic import BaseModel, Field

from rag.rag_config import DEFAULT_TOP_K


class RetrieveRequest(BaseModel):
    query: str = Field(min_length=1)
    top_k: int = Field(default=DEFAULT_TOP_K, ge=1, le=30)
    include_doc_types: list[str] | None = None
    scene: str = "pre_inquiry"


class RetrieveChunk(BaseModel):
    chunk_id: str | None = None
    doc_id: str | None = None
    doc_type: str | None = None
    title: str | None = None
    urgency_level: str | None = None
    related_departments: str | None = None
    applicable_population: str | None = None
    related_symptoms: str | None = None
    must_ask: list[str] = []
    red_flags: list[str] = []
    forbidden_actions: list[str] = []
    expected_response_points: list[str] = []
    doctor_record_fields: list[str] = []
    score: float | None = None
    final_score: float | None = None
    topic_alignment: str | None = None
    selection_reason: str | None = None
    chunk_text: str | None = None


class RetrieveResponse(BaseModel):
    success: bool
    query: str
    expanded_query: str | None = None
    doc_type_counts: dict[str, int] = {}
    used_query_expansion: bool = False
    chunks: list[RetrieveChunk] = []
    trace_meta: dict[str, Any] = {}
    error_message: str | None = None
