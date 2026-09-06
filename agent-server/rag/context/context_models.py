"""Context builder models for Medical RAG V3.1."""

from __future__ import annotations

from dataclasses import asdict, dataclass, field
from typing import Any


@dataclass(frozen=True)
class ContextPolicy:
    mode: str = "child_with_neighbors"
    neighbor_before: int = 1
    neighbor_after: int = 1
    parent_max_tokens: int = 700
    max_tokens: int = 1800
    max_units_per_parent: int = 1
    max_units_per_source: int = 4
    total_model_context_limit: int = 8192
    reserved_non_rag_tokens: int = 6400


@dataclass
class ContextUnit:
    context_unit_id: str
    source_id: str
    parent_id: str | None
    matched_child_ids: list[str]
    knowledge_origin: str
    doc_type: str
    heading_path: list[str]
    text: str
    token_count: int
    retrieval_rank: int
    best_rerank_score: float | None
    expansion_mode: str
    source_title: str
    publisher: str
    page_start: int | None
    page_end: int | None
    expanded_child_ids: list[str] = field(default_factory=list)
    source_url: str | None = None
    expansion_fallback_reason: str | None = None
    fallback_units: list[Any] = field(default_factory=list, repr=False)

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


@dataclass
class FinalContextPackage:
    context_units: list[ContextUnit]
    final_text: str
    token_count: int
    budget: int
    included_chunk_ids: list[str]
    included_parent_ids: list[str]
    included_source_ids: list[str]
    dropped_units: list[dict[str, str]]
    context_transform: dict[str, Any]
    context_hash: str
    trace: dict[str, Any]

    def to_dict(self) -> dict[str, Any]:
        return {
            "context_units": [unit.to_dict() for unit in self.context_units],
            "final_text": self.final_text,
            "token_count": self.token_count,
            "budget": self.budget,
            "included_chunk_ids": self.included_chunk_ids,
            "included_parent_ids": self.included_parent_ids,
            "included_source_ids": self.included_source_ids,
            "dropped_units": self.dropped_units,
            "context_transform": self.context_transform,
            "context_hash": self.context_hash,
            "trace": self.trace,
        }
