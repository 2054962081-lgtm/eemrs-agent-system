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
        answer = input(f"纭鍒犻櫎骞堕噸寤?collection {args.collection}? 杈撳叆 yes 缁х画: ")
        if answer.strip().lower() != "yes":
            print("宸插彇娑堛€?)
            return 1

    milvus.drop_collection()
    milvus.create_collection(provider.embedding_dim)
    print(f"Collection recreated: {args.collection}")
    print(f"embedding_dim: {provider.embedding_dim}")
    print("Milvus 鏁版嵁瀹為檯钀界洏浣嶇疆鍙栧喅浜?docker-compose.yml 鐨?volumes 閰嶇疆銆?)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
