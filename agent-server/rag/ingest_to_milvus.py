"""Validate, chunk, embed, and ingest local RAG knowledge into Milvus."""

from __future__ import annotations

import argparse
import sys
import time
from collections import Counter
from pathlib import Path

try:
    from tqdm import tqdm
except ImportError:  # pragma: no cover
    tqdm = None

from .build_chunks import build_chunks
from .embedding_provider import EmbeddingProvider
from .milvus_client import MedicalRagMilvus
from .rag_config import (
    DEFAULT_TOP_K,
    EMBEDDING_MODEL_NAME,
    KNOWLEDGE_BASE_DIR,
    MILVUS_COLLECTION_NAME,
    MILVUS_HOST,
    MILVUS_PORT,
    resolve_project_path,
)
from .validate_rag_knowledge import print_validation_result, validate_knowledge_dir


def batched(items: list[dict], size: int) -> list[list[dict]]:
    return [items[index:index + size] for index in range(0, len(items), size)]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reset", action="store_true")
    parser.add_argument("--batch-size", type=int, default=32)
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--knowledge-dir", default=KNOWLEDGE_BASE_DIR)
    parser.add_argument("--collection", default=MILVUS_COLLECTION_NAME)
    parser.add_argument("--top-k", type=int, default=DEFAULT_TOP_K)
    args = parser.parse_args()

    start = time.time()
    knowledge_dir = resolve_project_path(args.knowledge_dir)

    try:
        validation = validate_knowledge_dir(knowledge_dir)
        print_validation_result(validation)
        if validation.errors:
            return 1
        built = build_chunks(validation.documents)
    except Exception as exc:
        print(f"入库前处理失败: {exc}", file=sys.stderr)
        return 2

    chunk_ids = [chunk["chunk_id"] for chunk in built.chunks]
    duplicate_chunk_count = len(chunk_ids) - len(set(chunk_ids))
    if duplicate_chunk_count:
        print(f"发现重复 chunk_id 数量: {duplicate_chunk_count}，停止入库。", file=sys.stderr)
        return 2

    print(f"chunk 总数: {len(built.chunks)}")
    print("每类 doc_type chunk 数量:")
    for doc_type, count in sorted(built.doc_type_counts.items()):
        print(f"- {doc_type}: {count}")

    if args.dry_run:
        print("dry-run 已通过：只完成校验和 chunk 构建，未加载 embedding，未连接 Milvus，未写入数据。")
        print(f"embedding 模型配置: {EMBEDDING_MODEL_NAME}")
        print("Milvus 数据实际落盘位置取决于 docker-compose.yml 的 volumes 配置。")
        print(f"耗时: {time.time() - start:.2f}s")
        return 0

    try:
        provider = EmbeddingProvider()
        milvus = MedicalRagMilvus(MILVUS_HOST, MILVUS_PORT, args.collection)
        milvus.connect()
        milvus.ensure_collection(provider.embedding_dim, reset=args.reset)

        existing = milvus.existing_chunk_ids(chunk_ids)
        if existing:
            print(
                f"collection 中已存在 {len(existing)} 个 chunk_id，请使用 --reset 重建后再入库。示例: "
                f"python -m rag.ingest_to_milvus --reset",
                file=sys.stderr,
            )
            return 3

        inserted = 0
        failed = 0
        batches = batched(built.chunks, args.batch_size)
        iterator = tqdm(batches, desc="Embedding+insert") if tqdm else batches
        for batch in iterator:
            texts = [row["chunk_text"] for row in batch]
            vectors = provider.encode_texts(texts, batch_size=args.batch_size)
            rows = []
            for row, vector in zip(batch, vectors):
                entity = {key: value for key, value in row.items() if key != "source_path"}
                entity["embedding"] = vector
                rows.append(entity)
            try:
                inserted += milvus.insert(rows)
            except Exception as exc:
                failed += len(rows)
                print(f"批量插入失败: {exc}", file=sys.stderr)
                raise
        milvus.flush_and_load()
    except Exception as exc:
        print(f"Milvus 入库失败: {exc}", file=sys.stderr)
        return 4

    elapsed = time.time() - start
    print("入库完成")
    print(f"JSON 文件总数: {validation.total_files}")
    print(f"chunk 总数: {len(built.chunks)}")
    print(f"embedding 模型名称: {provider.model_name}")
    print(f"embedding 维度: {provider.embedding_dim}")
    print(f"Milvus: {MILVUS_HOST}:{MILVUS_PORT}")
    print(f"collection: {args.collection}")
    print(f"成功写入数量: {inserted}")
    print(f"失败数量: {failed}")
    print("每类 doc_type 写入数量:")
    for doc_type, count in sorted(Counter(row["doc_type"] for row in built.chunks).items()):
        print(f"- {doc_type}: {count}")
    print("Milvus 数据实际落盘位置取决于 docker-compose.yml 的 volumes 配置。")
    print(f"耗时: {elapsed:.2f}s")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
