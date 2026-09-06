"""List local Milvus RAG entries for manual inspection.

Examples:
    python -m rag.list_rag_entries
    python -m rag.list_rag_entries --doc-type red_flag
    python -m rag.list_rag_entries --doc-type symptom_inquiry --limit 100
    python -m rag.list_rag_entries --show-content
"""

from __future__ import annotations

import argparse
import sys
from collections import Counter
from typing import Any

from .milvus_client import MedicalRagMilvus
from .rag_config import MILVUS_COLLECTION_NAME, MILVUS_HOST, MILVUS_PORT
from .rag_schema import ALLOWED_DOC_TYPES


OUTPUT_FIELDS = [
    "chunk_id",
    "doc_id",
    "doc_type",
    "title",
    "urgency_level",
    "related_departments",
    "chunk_text",
]


def summarize(text: Any, limit: int = 300) -> str:
    clean = " ".join(str(text or "").split())
    return clean if len(clean) <= limit else clean[:limit] + "..."


def count_entities(milvus: MedicalRagMilvus) -> int:
    rows = milvus.client.query(
        collection_name=milvus.collection_name,
        filter="",
        output_fields=["count(*)"],
    )
    if rows and "count(*)" in rows[0]:
        return int(rows[0]["count(*)"])
    return 0


def query_entries(milvus: MedicalRagMilvus, limit: int, doc_type: str | None = None) -> list[dict[str, Any]]:
    filter_expr = f'doc_type == "{doc_type}"' if doc_type else ""
    return milvus.client.query(
        collection_name=milvus.collection_name,
        filter=filter_expr,
        output_fields=OUTPUT_FIELDS,
        limit=limit,
    )


def doc_type_counts(milvus: MedicalRagMilvus) -> Counter[str]:
    total = count_entities(milvus)
    rows = milvus.client.query(
        collection_name=milvus.collection_name,
        filter="",
        output_fields=["doc_type"],
        limit=max(total, 1),
    )
    return Counter(str(row.get("doc_type") or "") for row in rows)


def print_entry(index: int, row: dict[str, Any], show_content: bool) -> None:
    print(f"\n#{index}")
    print(f"chunk_id: {row.get('chunk_id')}")
    print(f"doc_id: {row.get('doc_id')}")
    print(f"doc_type: {row.get('doc_type')}")
    print(f"title: {row.get('title')}")
    print(f"urgency_level: {row.get('urgency_level')}")
    print(f"related_departments: {row.get('related_departments')}")
    chunk_text = str(row.get("chunk_text") or "")
    print(f"chunk_text: {chunk_text if show_content else summarize(chunk_text)}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--doc-type", choices=sorted(ALLOWED_DOC_TYPES))
    parser.add_argument("--limit", type=int, default=50)
    parser.add_argument("--show-content", action="store_true")
    parser.add_argument("--collection", default=MILVUS_COLLECTION_NAME)
    args = parser.parse_args()

    if args.limit <= 0:
        print("--limit 必须大于 0。", file=sys.stderr)
        return 2

    try:
        milvus = MedicalRagMilvus(MILVUS_HOST, MILVUS_PORT, args.collection)
        milvus.connect()
        exists = milvus.has_collection()
        print(f"collection: {args.collection}")
        print(f"collection exists: {exists}")
        if not exists:
            print(
                f"collection 不存在: {args.collection}。请先执行 python -m rag.ingest_to_milvus --reset 入库。",
                file=sys.stderr,
            )
            return 2
        milvus.client.load_collection(args.collection)
        total = count_entities(milvus)
        counts = doc_type_counts(milvus)
        rows = query_entries(milvus, limit=args.limit, doc_type=args.doc_type)
    except Exception as exc:
        print(
            "无法连接 Milvus 或查询 collection，请确认 Docker 容器已启动并暴露 19530：\n"
            "  docker ps\n"
            f"错误详情: {exc}",
            file=sys.stderr,
        )
        return 2

    print(f"num_entities: {total}")
    print("doc_type counts:")
    for doc_type, count in sorted(counts.items()):
        print(f"- {doc_type}: {count}")
    if args.doc_type:
        print(f"filter doc_type: {args.doc_type}")
    print(f"display limit: {args.limit}")
    print(f"displayed entries: {len(rows)}")

    for index, row in enumerate(rows, 1):
        print_entry(index, row, args.show_content)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
