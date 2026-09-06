"""Structured document and chunk models for Medical RAG V3 ingestion."""

from __future__ import annotations

from dataclasses import asdict, dataclass, field
from typing import Any


@dataclass
class SourceRecord:
    source_id: str
    title: str
    publisher: str
    source_domain: str
    source_url: str
    download_url: str | None
    document_type: str
    language: str
    publication_date: str | None
    version: str | None
    retrieved_at: str
    status: str
    superseded_by: str | None
    license: str | None
    license_verified: bool
    license_status: str
    sha256: str | None
    allowed_for_ingestion: bool
    rejection_reason: str | None = None
    local_path: str | None = None
    mime_type: str | None = None
    parser_status: str | None = None

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


@dataclass
class DocumentBlock:
    block_id: str
    page: int | None
    block_type: str
    text: str
    heading_path: list[str]
    order: int
    table_title: str | None = None
    table_headers: list[str] = field(default_factory=list)

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


@dataclass
class ParsedDocument:
    source: SourceRecord
    pages: list[dict[str, Any]]
    blocks: list[DocumentBlock]
    parser_name: str
    parser_status: str = "parsed"
    warnings: list[str] = field(default_factory=list)

    def to_dict(self) -> dict[str, Any]:
        return {
            "source": self.source.to_dict(),
            "pages": self.pages,
            "blocks": [block.to_dict() for block in self.blocks],
            "parser_name": self.parser_name,
            "parser_status": self.parser_status,
            "warnings": self.warnings,
        }


@dataclass(frozen=True)
class ProtectedSpan:
    start: int
    end: int
    text: str
    kind: str

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


@dataclass
class ParentChunk:
    parent_id: str
    source_id: str
    source_title: str
    heading_path: list[str]
    page_start: int | None
    page_end: int | None
    text_hash: str

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


@dataclass
class MedicalChunkV2:
    chunk_id: str
    parent_id: str
    source_id: str
    source_title: str
    publisher: str
    publication_date: str | None
    version: str | None
    source_url: str
    document_type: str
    source_status: str
    license_status: str
    heading_path: list[str]
    page_start: int | None
    page_end: int | None
    block_types: list[str]
    chunk_text: str
    embedding_text: str
    char_count: int
    token_count: int
    embedding_token_count: int
    embedding_prefix_tokens: int
    protected_terms: list[str]
    abbreviations: list[str]
    numeric_unit_spans: list[str]
    split_reason: str
    semantic_boundary_type: str
    knowledge_origin: str = "external_evidence"
    source_span: dict[str, Any] = field(default_factory=dict)
    overlap_tokens: int = 0
    overlap_ratio: float = 0.0

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)
