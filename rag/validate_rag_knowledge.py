"""Validate local RAG knowledge JSON files.

Run from project root:
    python -m rag.validate_rag_knowledge
"""

from __future__ import annotations

import argparse
import json
import sys
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from .rag_config import KNOWLEDGE_BASE_DIR, resolve_project_path
from .rag_schema import ALLOWED_DOC_TYPES, DIR_DOC_TYPE_MAP, REQUIRED_FIELDS


@dataclass
class ValidationResult:
    total_files: int
    valid_files: int
    errors: list[tuple[Path, str]]
    doc_type_counts: Counter[str]
    directory_counts: Counter[str]
    readme_exists: bool
    documents: list[tuple[Path, dict[str, Any]]]


def find_json_files(knowledge_dir: Path) -> list[Path]:
    return sorted(knowledge_dir.rglob("*.json"))


def _is_blank(value: Any) -> bool:
    return value is None or (isinstance(value, str) and not value.strip())


def validate_document(path: Path, data: Any, seen_doc_ids: set[str]) -> list[str]:
    errors: list[str] = []
    if not isinstance(data, dict):
        return ["JSON root must be an object"]

    for field in REQUIRED_FIELDS:
        if field not in data:
            errors.append(f"missing required field: {field}")

    doc_id = data.get("doc_id")
    if _is_blank(doc_id):
        errors.append("doc_id is empty")
    elif str(doc_id) in seen_doc_ids:
        errors.append(f"duplicate doc_id: {doc_id}")

    doc_type = data.get("doc_type")
    if doc_type not in ALLOWED_DOC_TYPES:
        errors.append(f"invalid doc_type: {doc_type}")

    chunk_text = data.get("chunk_text")
    if _is_blank(chunk_text):
        errors.append("chunk_text is empty")

    parent_name = path.parent.name
    expected_doc_type = DIR_DOC_TYPE_MAP.get(parent_name)
    if expected_doc_type and doc_type != expected_doc_type:
        errors.append(
            f"doc_type {doc_type!r} does not match directory {parent_name!r}; expected {expected_doc_type!r}"
        )
    return errors


def validate_knowledge_dir(knowledge_dir: Path) -> ValidationResult:
    if not knowledge_dir.exists():
        raise FileNotFoundError("鏈壘鍒?rag_knowledge 鐩綍锛岃鍏堝垱寤烘湰鍦?JSON 鐭ヨ瘑鏂囦欢銆?)
    if not knowledge_dir.is_dir():
        raise NotADirectoryError(f"Knowledge path is not a directory: {knowledge_dir}")

    json_files = find_json_files(knowledge_dir)
    errors: list[tuple[Path, str]] = []
    doc_type_counts: Counter[str] = Counter()
    directory_counts: Counter[str] = Counter()
    documents: list[tuple[Path, dict[str, Any]]] = []
    seen_doc_ids: set[str] = set()

    for path in json_files:
        directory_counts[path.parent.name] += 1
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except json.JSONDecodeError as exc:
            errors.append((path, f"invalid JSON: {exc}"))
            continue

        doc_errors = validate_document(path, data, seen_doc_ids)
        if doc_errors:
            for error in doc_errors:
                errors.append((path, error))
            continue

        seen_doc_ids.add(str(data["doc_id"]))
        doc_type_counts[str(data["doc_type"])] += 1
        documents.append((path, data))

    return ValidationResult(
        total_files=len(json_files),
        valid_files=len(documents),
        errors=errors,
        doc_type_counts=doc_type_counts,
        directory_counts=directory_counts,
        readme_exists=(knowledge_dir / "README.md").exists(),
        documents=documents,
    )


def print_validation_result(result: ValidationResult) -> None:
    print(f"JSON 鏂囦欢鎬绘暟: {result.total_files}")
    print(f"鏈夋晥鏂囦欢鏁? {result.valid_files}")
    print(f"閿欒鏂囦欢鏁? {len(result.errors)}")
    print(f"README.md: {'瀛樺湪' if result.readme_exists else '涓嶅瓨鍦?}")
    print("姣忕被鐩綍 JSON 鏂囦欢鏁伴噺:")
    for directory, count in sorted(result.directory_counts.items()):
        print(f"- {directory}: {count}")
    print("姣忕被 doc_type 鏈夋晥鏂囦欢鏁伴噺:")
    for doc_type, count in sorted(result.doc_type_counts.items()):
        print(f"- {doc_type}: {count}")
    if result.errors:
        print("閿欒璇︽儏:")
        for path, error in result.errors:
            print(f"- {path}: {error}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--knowledge-dir", default=KNOWLEDGE_BASE_DIR)
    args = parser.parse_args()

    knowledge_dir = resolve_project_path(args.knowledge_dir)
    try:
        result = validate_knowledge_dir(knowledge_dir)
    except (FileNotFoundError, NotADirectoryError) as exc:
        print(str(exc), file=sys.stderr)
        return 2

    print_validation_result(result)
    return 1 if result.errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
