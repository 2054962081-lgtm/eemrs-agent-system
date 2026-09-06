"""Token-aware, structure-aware Medical RAG V3 chunker."""

from __future__ import annotations

import hashlib
import re
from collections import defaultdict
from dataclasses import dataclass

from rag.ingestion.medical_term_protector import MedicalTermProtector, is_inside_protected_span
from rag.ingestion.tokenizer_utils import BgeTokenizer
from rag.schema.medical_ingestion import DocumentBlock, MedicalChunkV2, ParentChunk, ParsedDocument, ProtectedSpan


@dataclass(frozen=True)
class ChunkingConfig:
    child_target_tokens: int = 320
    child_soft_min_tokens: int = 120
    child_soft_max_tokens: int = 420
    child_hard_max_tokens: int = 480


def stable_hash(value: str, length: int = 12) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()[:length]


class MedicalSemanticChunker:
    def __init__(self, tokenizer: BgeTokenizer | None = None, config: ChunkingConfig | None = None):
        self.tokenizer = tokenizer or BgeTokenizer()
        self.config = config or ChunkingConfig()
        self.protector = MedicalTermProtector()

    def chunk(self, document: ParsedDocument) -> tuple[list[ParentChunk], list[MedicalChunkV2]]:
        parents: list[ParentChunk] = []
        chunks: list[MedicalChunkV2] = []
        grouped: dict[tuple[str, ...], list[DocumentBlock]] = defaultdict(list)
        for block in document.blocks:
            if block.block_type == "heading":
                continue
            grouped[tuple(block.heading_path or [document.source.title])].append(block)

        for heading_path, blocks in grouped.items():
            parent_text = "\n".join(block.text for block in blocks)
            pages = [block.page for block in blocks if block.page is not None]
            parent_id = f"{document.source.source_id}_p_{stable_hash('>'.join(heading_path))}"
            parents.append(
                ParentChunk(
                    parent_id=parent_id,
                    source_id=document.source.source_id,
                    source_title=document.source.title,
                    heading_path=list(heading_path),
                    page_start=min(pages) if pages else None,
                    page_end=max(pages) if pages else None,
                    text_hash=stable_hash(parent_text, 16),
                )
            )
            units = self._atomic_units(blocks)
            current: list[DocumentBlock] = []
            for unit in units:
                if self._unit_tokens(unit) > self._content_budget(list(heading_path)):
                    if current:
                        chunks.append(self._make_chunk(document, parent_id, list(heading_path), current, len(chunks), "normal"))
                        current = []
                    for split_unit in self._split_long_unit(unit, list(heading_path)):
                        chunks.append(
                            self._make_chunk(
                                document,
                                parent_id,
                                list(heading_path),
                                [split_unit],
                                len(chunks),
                                "atomic_unit_exceeds_hard_max",
                            )
                    )
                    continue
                candidate = current + [unit]
                if current and (
                    self._unit_tokens_many(candidate) > self.config.child_soft_max_tokens
                    or self._embedding_tokens(list(heading_path), candidate) > self.config.child_hard_max_tokens
                ):
                    chunks.append(self._make_chunk(document, parent_id, list(heading_path), current, len(chunks), "normal"))
                    current = [unit]
                else:
                    current = candidate
            if current:
                chunks.append(self._make_chunk(document, parent_id, list(heading_path), current, len(chunks), "normal"))
        return parents, chunks

    def _atomic_units(self, blocks: list[DocumentBlock]) -> list[DocumentBlock]:
        units: list[DocumentBlock] = []
        table_headers: list[str] = []
        for block in blocks:
            if block.block_type == "table_row":
                if not table_headers:
                    table_headers = [part.strip() for part in block.text.split("|")]
                    continue
                text = " | ".join(table_headers) + "\n" + block.text
                units.append(DocumentBlock(**{**block.to_dict(), "text": text, "table_headers": table_headers}))
                continue
            table_headers = []
            units.append(block)
        return units

    def _unit_tokens(self, block: DocumentBlock) -> int:
        return self.tokenizer.count(block.text)

    def _unit_tokens_many(self, blocks: list[DocumentBlock]) -> int:
        return self.tokenizer.count("\n".join(block.text for block in blocks))

    def _prefix(self, heading_path: list[str]) -> str:
        return "[" + "/".join(heading_path[1:] or heading_path) + "]\n"

    def _content_budget(self, heading_path: list[str]) -> int:
        return max(40, self.config.child_hard_max_tokens - self.tokenizer.count(self._prefix(heading_path)))

    def _embedding_tokens(self, heading_path: list[str], blocks: list[DocumentBlock]) -> int:
        return self.tokenizer.count(self._prefix(heading_path) + "\n".join(block.text for block in blocks))

    def _split_long_unit(self, block: DocumentBlock, heading_path: list[str]) -> list[DocumentBlock]:
        text = block.text
        spans = self.protector.detect(text)
        pieces = safe_split_text(text, spans, self.tokenizer, self._content_budget(heading_path))
        return [DocumentBlock(**{**block.to_dict(), "block_id": f"{block.block_id}_s{i:02d}", "text": piece}) for i, piece in enumerate(pieces, 1)]

    def _make_chunk(
        self,
        document: ParsedDocument,
        parent_id: str,
        heading_path: list[str],
        blocks: list[DocumentBlock],
        index: int,
        split_reason: str,
    ) -> MedicalChunkV2:
        chunk_text = "\n".join(block.text for block in blocks).strip()
        prefix = self._prefix(heading_path)
        embedding_text = prefix + chunk_text
        spans = self.protector.detect(chunk_text)
        pages = [block.page for block in blocks if block.page is not None]
        source = document.source
        chunk_id = (
            f"{source.source_id}_c_{stable_hash('>'.join(heading_path))}_"
            f"{index + 1:04d}_{stable_hash(chunk_text)}"
        )
        return MedicalChunkV2(
            chunk_id=chunk_id,
            parent_id=parent_id,
            source_id=source.source_id,
            source_title=source.title,
            publisher=source.publisher,
            publication_date=source.publication_date,
            version=source.version,
            source_url=source.source_url,
            document_type=source.document_type,
            source_status=source.status,
            license_status=source.license_status,
            heading_path=heading_path,
            page_start=min(pages) if pages else None,
            page_end=max(pages) if pages else None,
            block_types=sorted({block.block_type for block in blocks}),
            chunk_text=chunk_text,
            embedding_text=embedding_text,
            char_count=len(chunk_text),
            token_count=self.tokenizer.count(chunk_text),
            embedding_token_count=self.tokenizer.count(embedding_text),
            embedding_prefix_tokens=self.tokenizer.count(prefix),
            protected_terms=[span.text for span in spans if span.kind != "numeric_unit"],
            abbreviations=[span.text for span in spans if span.kind == "abbreviation"],
            numeric_unit_spans=[span.text for span in spans if span.kind == "numeric_unit"],
            split_reason=split_reason,
            semantic_boundary_type="block",
            source_span={"block_ids": [block.block_id for block in blocks]},
        )


def safe_split_text(text: str, spans: list[ProtectedSpan], tokenizer: BgeTokenizer, hard_max: int) -> list[str]:
    pieces: list[str] = []
    remaining = text.strip()
    while remaining and tokenizer.count(remaining) > hard_max:
        char_guess = max(1, int(len(remaining) * hard_max / max(tokenizer.count(remaining), 1)))
        boundary = nearest_safe_boundary(remaining, char_guess, spans)
        pieces.append(remaining[:boundary].strip())
        remaining = remaining[boundary:].strip()
        spans = [ProtectedSpan(max(0, s.start - boundary), max(0, s.end - boundary), s.text, s.kind) for s in spans if s.end > boundary]
    if remaining:
        pieces.append(remaining)
    return [piece for piece in pieces if piece]


def nearest_safe_boundary(text: str, candidate: int, spans: list[ProtectedSpan]) -> int:
    protected = is_inside_protected_span(candidate, spans)
    if protected:
        candidate = protected.end
    for pattern in (r"[。！？；]\s*", r"\n+", r"，\s*"):
        matches = [m.end() for m in re.finditer(pattern, text)]
        before = [m for m in matches if candidate // 2 <= m <= min(len(text), candidate + 80)]
        if before:
            return before[0]
    return min(len(text), max(1, candidate))
