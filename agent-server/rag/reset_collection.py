"""Drop and recreate the medical RAG Milvus collection."""

from __future__ import annotations

import argparse

from .embedding_provider import EmbeddingProvider
from .milvus_client import MedicalRagMilvus
from .rag_config import MILVUS_COLLECTION_NAME, MILVUS_HOST, MILVUS_PORT


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--yes", action="store_true", help="Skip deletion confirmation")
    parser.add_argument("--collection", default=MILVUS_COLLECTION_NAME)
    args = parser.parse_args()

    provider = EmbeddingProvider()
    milvus = MedicalRagMilvus(MILVUS_HOST, MILVUS_PORT, args.collection)
    milvus.connect()

    if milvus.has_collection() and not args.yes:
        answer = input(f"确认删除并重建 collection {args.collection}? 输入 yes 继续: ")
        if answer.strip().lower() != "yes":
            print("已取消。")
            return 1

    milvus.drop_collection()
    milvus.create_collection(provider.embedding_dim)
    print(f"Collection recreated: {args.collection}")
    print(f"embedding_dim: {provider.embedding_dim}")
    print("Milvus 数据实际落盘位置取决于 docker-compose.yml 的 volumes 配置。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
