"""Local parent/child store for Medical RAG V3 external evidence artifacts."""

from __future__ import annotations

import json
import re
from dataclasses import dataclass
from pathlib import Path
from statistics import mean
from typing import Any

from rag.ingestion.chunk_quality_audit import distribution
from rag.ingestion.tokenizer_utils import BgeTokenizer
from rag.rag_config import PROJECT_ROOT


@dataclass(frozen=True)
class StoredParent:
    parent_id: str
    source_id: str
    source_title: str
    heading_path: list[str]
    page_start: int | None
    page_end: int | None
    text_hash: str | None
    text: str
    token_count: int
    text_source: str = "reconstructed_from_children"


@dataclass(frozen=True)
class StoredChild:
    chunk_id: str
    child_id: str
    parent_id: str
    source_id: str
    source_title: str
    publisher: str
    publication_date: str | None
    version: str | None
    source_url: str
    document_type: str
    knowledge_origin: str
    heading_path: list[str]
    page_start: int | None
    page_end: int | None
    chunk_text: str
    embedding_text: str
    token_count: int
    embedding_token_count: int
    child_order: int
    retrieval_rank: int | None = None
    score: float | None = None

    @classmethod
    def from_dict(cls, row: dict[str, Any]) -> "StoredChild":
        chunk_id = str(row["chunk_id"])
        return cls(
            chunk_id=chunk_id,
            child_id=chunk_id,
            parent_id=str(row["parent_id"]),
            source_id=str(row["source_id"]),
            source_title=str(row.get("source_title") or ""),
            publisher=str(row.get("publisher") or ""),
            publication_date=row.get("publication_date"),
            version=row.get("version"),
            source_url=str(row.get("source_url") or ""),
            document_type=str(row.get("document_type") or ""),
            knowledge_origin=str(row.get("knowledge_origin") or "external_evidence"),
            heading_path=parse_heading_path(row.get("heading_path")),
            page_start=row.get("page_start"),
            page_end=row.get("page_end"),
            chunk_text=str(row.get("chunk_text") or ""),
            embedding_text=str(row.get("embedding_text") or row.get("chunk_text") or ""),
            token_count=int(row.get("token_count") or 0),
            embedding_token_count=int(row.get("embedding_token_count") or 0),
            child_order=parse_child_order(chunk_id),
        )

    def with_rank(self, rank: int, score: float) -> "StoredChild":
        return StoredChild(**{**self.__dict__, "retrieval_rank": rank, "score": score})


def parse_child_order(chunk_id: str) -> int:
    match = re.search(r"_c_[0-9a-f]+_(\d{4})_", chunk_id)
    return int(match.group(1)) if match else 0


def parse_heading_path(value: Any) -> list[str]:
    if value is None:
        return []
    if isinstance(value, list):
        return [str(item) for item in value if str(item).strip()]
    text = str(value).strip()
    if not text:
        return []
    try:
        parsed = json.loads(text)
        if isinstance(parsed, list):
            return [str(item) for item in parsed if str(item).strip()]
    except json.JSONDecodeError:
        pass
    return [part.strip() for part in re.split(r"\s*>\s*|\s*/\s*", text) if part.strip()]


def latest_v3_artifact(root: Path | None = None) -> Path:
    artifacts = root or PROJECT_ROOT / "rag" / "eval" / "artifacts"
    candidates = sorted(artifacts.glob("medical_ingestion_v3_*"), key=lambda path: path.name, reverse=True)
    for candidate in candidates:
        if (candidate / "parents.jsonl").exists() and (candidate / "semantic_chunks.jsonl").exists():
            return candidate
    raise FileNotFoundError("No medical_ingestion_v3_* artifact with parents.jsonl and semantic_chunks.jsonl found")


class ParentStore:
    def __init__(self, artifact_dir: Path | None = None, tokenizer: BgeTokenizer | None = None):
        self.artifact_dir = artifact_dir or latest_v3_artifact()
        self.tokenizer = tokenizer or BgeTokenizer()
        self.parent_path, self.child_path = resolve_parent_child_paths(self.artifact_dir)
        self.parent_rows = read_jsonl(self.parent_path)
        self.child_rows = read_jsonl(self.child_path)
        self.children = [StoredChild.from_dict(row) for row in self.child_rows]
        self.children_by_id = {child.chunk_id: child for child in self.children}
        self.children_by_parent: dict[str, list[StoredChild]] = {}
        for child in self.children:
            self.children_by_parent.setdefault(child.parent_id, []).append(child)
        for parent_id in list(self.children_by_parent):
            self.children_by_parent[parent_id].sort(key=lambda child: (child.child_order, child.chunk_id))
        self.parents_by_id = self._build_parents()

    def _build_parents(self) -> dict[str, StoredParent]:
        parents: dict[str, StoredParent] = {}
        for row in self.parent_rows:
            parent_id = str(row["parent_id"])
            text = "\n".join(child.chunk_text for child in self.children_by_parent.get(parent_id, []))
            parents[parent_id] = StoredParent(
                parent_id=parent_id,
                source_id=str(row.get("source_id") or ""),
                source_title=str(row.get("source_title") or ""),
                heading_path=list(row.get("heading_path") or []),
                page_start=row.get("page_start"),
                page_end=row.get("page_end"),
                text_hash=row.get("text_hash"),
                text=text,
                token_count=self.tokenizer.count(text) if text else 0,
            )
        return parents

    def get_parent(self, parent_id: str) -> StoredParent | None:
        return self.parents_by_id.get(parent_id)

    def get_child(self, child_id: str) -> StoredChild | None:
        return self.children_by_id.get(child_id)

    def get_children(self, parent_id: str) -> list[StoredChild]:
        return list(self.children_by_parent.get(parent_id, []))

    def get_sibling_children(self, parent_id: str, child_id: str, before: int, after: int) -> list[StoredChild]:
        siblings = self.children_by_parent.get(parent_id, [])
        index = next((idx for idx, child in enumerate(siblings) if child.chunk_id == child_id), None)
        if index is None:
            return []
        start = max(0, index - before)
        end = min(len(siblings), index + after + 1)
        return list(siblings[start:end])

    def audit(self) -> dict[str, Any]:
        parent_ids = set(self.parents_by_id)
        child_parent_ids = [child.parent_id for child in self.children]
        child_ids = [child.chunk_id for child in self.children]
        children_per_parent = [len(self.children_by_parent.get(parent_id, [])) for parent_id in sorted(parent_ids)]
        parent_tokens = [parent.token_count for parent in self.parents_by_id.values()]
        child_tokens = [child.token_count for child in self.children]
        invalid_parent_refs = [parent_id for parent_id in child_parent_ids if parent_id not in parent_ids]
        parents_without_child = [parent_id for parent_id in parent_ids if not self.children_by_parent.get(parent_id)]
        return {
            "artifact_dir": str(self.artifact_dir),
            "parent_count": len(parent_ids),
            "child_count": len(self.children),
            "children_per_parent": {
                "min": min(children_per_parent) if children_per_parent else 0,
                "mean": mean(children_per_parent) if children_per_parent else 0,
                "p50": distribution(children_per_parent)["p50"] if children_per_parent else 0,
                "max": max(children_per_parent) if children_per_parent else 0,
            },
            "parent_token_distribution": distribution(parent_tokens),
            "child_token_distribution": distribution(child_tokens),
            "orphan_child_count": len(invalid_parent_refs),
            "parent_without_child_count": len(parents_without_child),
            "duplicate_child_id_count": len(child_ids) - len(set(child_ids)),
            "invalid_parent_ref_count": len(invalid_parent_refs),
            "parent_text_saved_in_v3_parent_file": False,
            "parent_text_source": "reconstructed_from_children",
            "has_child_order": all(child.child_order > 0 for child in self.children),
            "has_heading_path": all(bool(child.heading_path) for child in self.children),
            "has_source_reverse_lookup": all(
                child.source_id and child.parent_id in self.parents_by_id and self.parents_by_id[child.parent_id].source_id == child.source_id
                for child in self.children
            ),
        }


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    rows = []
    with path.open("r", encoding="utf-8") as handle:
        for line in handle:
            if line.strip():
                rows.append(json.loads(line))
    return rows


def resolve_parent_child_paths(artifact_dir: Path) -> tuple[Path, Path]:
    candidates = [
        (artifact_dir / "parents.jsonl", artifact_dir / "semantic_chunks.jsonl"),
        (artifact_dir / "cleaned_parents.jsonl", artifact_dir / "cleaned_semantic_chunks.jsonl"),
        (artifact_dir / "staging" / "cleaned_parents.jsonl", artifact_dir / "staging" / "cleaned_semantic_chunks.jsonl"),
    ]
    for parent_path, child_path in candidates:
        if parent_path.exists() and child_path.exists():
            return parent_path, child_path
    raise FileNotFoundError(f"No parent/child JSONL files found under {artifact_dir}")
