"""Build normalized Milvus chunks from validated RAG knowledge JSON files."""

from __future__ import annotations

import json
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from .rag_config import (
    CHUNK_OVERLAP,
    CHUNK_SPLIT_THRESHOLD,
    MAX_CHUNK_TEXT_LENGTH,
    MAX_CONTENT_JSON_LENGTH,
)


@dataclass
class BuiltChunks:
    chunks: list[dict[str, Any]]
    doc_type_counts: Counter[str]


def stringify_list(value: Any) -> str:
    if value is None:
        return ""
    if isinstance(value, list):
        return "锛?.join(str(item) for item in value)
    return str(value)


def compact_json(data: dict[str, Any]) -> str:
    return json.dumps(data, ensure_ascii=False, separators=(",", ":"))


def split_text(text: str, threshold: int = CHUNK_SPLIT_THRESHOLD, overlap: int = CHUNK_OVERLAP) -> list[str]:
    text = text.strip()
    if len(text) <= threshold:
        return [text]

    chunks: list[str] = []
    start = 0
    while start < len(text):
        end = min(start + threshold, len(text))
        if end < len(text):
            paragraph_break = text.rfind("\n", start, end)
            sentence_break = max(text.rfind("銆?, start, end), text.rfind("锛?, start, end))
            split_at = max(paragraph_break, sentence_break)
            if split_at > start + threshold // 2:
                end = split_at + 1
        chunks.append(text[start:end].strip())
        if end >= len(text):
            break
        start = max(0, end - overlap)
    return [chunk for chunk in chunks if chunk]


def build_chunks(documents: list[tuple[Path, dict[str, Any]]]) -> BuiltChunks:
    chunks: list[dict[str, Any]] = []
    doc_type_counts: Counter[str] = Counter()
    seen_chunk_ids: set[str] = set()

    for path, data in documents:
        doc_id = str(data["doc_id"])
        doc_type = str(data["doc_type"])
        content_json = compact_json(data)
        if len(content_json) > MAX_CONTENT_JSON_LENGTH:
            raise ValueError(
                f"content_json too long ({len(content_json)} > {MAX_CONTENT_JSON_LENGTH}) for {path}"
            )

        chunk_text = str(data.get("chunk_text") or "").strip()
        if not chunk_text:
            raise ValueError(f"chunk_text is empty for {path}")

        text_parts = split_text(chunk_text)
        for index, text_part in enumerate(text_parts, 1):
            if len(text_part) > MAX_CHUNK_TEXT_LENGTH:
                raise ValueError(
                    f"chunk_text too long after split ({len(text_part)} > {MAX_CHUNK_TEXT_LENGTH}) for {path}"
                )
            chunk_id = f"{doc_id}_CHUNK_{index:03d}"
            if chunk_id in seen_chunk_ids:
                raise ValueError(f"duplicate chunk_id generated: {chunk_id}")
            seen_chunk_ids.add(chunk_id)
            chunks.append(
                {
                    "chunk_id": chunk_id,
                    "doc_id": doc_id,
                    "doc_type": doc_type,
                    "title": str(data.get("title") or ""),
                    "version": str(data.get("version") or ""),
                    "language": str(data.get("language") or ""),
                    "source_type": str(data.get("source_type") or ""),
                    "applicable_population": stringify_list(data.get("applicable_population")),
                    "related_symptoms": stringify_list(data.get("related_symptoms")),
                    "related_departments": stringify_list(data.get("related_departments")),
                    "urgency_level": str(data.get("urgency_level") or ""),
                    "content_json": content_json,
                    "chunk_text": text_part,
                    "source_path": str(path),
                }
            )
            doc_type_counts[doc_type] += 1

    return BuiltChunks(chunks=chunks, doc_type_counts=doc_type_counts)
