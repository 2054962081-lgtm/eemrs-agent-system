"""Search the medical RAG Milvus collection for validation."""

from __future__ import annotations

import argparse
import sys

from .embedding_provider import EmbeddingProvider
from .milvus_client import MedicalRagMilvus
from .rag_config import DEFAULT_TOP_K, MILVUS_COLLECTION_NAME, MILVUS_HOST, MILVUS_PORT


def summarize(text: str, limit: int = 300) -> str:
    text = " ".join(str(text or "").split())
    return text if len(text) <= limit else text[:limit] + "..."


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--query", required=True)
    parser.add_argument("--top-k", type=int, default=DEFAULT_TOP_K)
    parser.add_argument("--doc-type")
    parser.add_argument("--population")
    parser.add_argument("--symptom")
    parser.add_argument("--collection", default=MILVUS_COLLECTION_NAME)
    args = parser.parse_args()

    try:
        provider = EmbeddingProvider()
        vector = provider.encode_texts([args.query])[0]
        milvus = MedicalRagMilvus(MILVUS_HOST, MILVUS_PORT, args.collection)
        milvus.connect()
        results = milvus.search(
            vector,
            top_k=args.top_k,
            doc_type=args.doc_type,
            population=args.population,
            symptom=args.symptom,
        )
    except Exception as exc:
        print(f"妫€绱㈠け璐? {exc}", file=sys.stderr)
        return 2

    print(f"query: {args.query}")
    print(f"top_k: {args.top_k}")
    for index, hit in enumerate(results, 1):
        entity = hit.get("entity") or hit
        score = hit.get("distance", hit.get("score", ""))
        print(f"\n#{index} score={score}")
        print(f"doc_id: {entity.get('doc_id')}")
        print(f"chunk_id: {entity.get('chunk_id')}")
        print(f"doc_type: {entity.get('doc_type')}")
        print(f"title: {entity.get('title')}")
        print(f"urgency_level: {entity.get('urgency_level')}")
        print(f"related_departments: {entity.get('related_departments')}")
        print(f"chunk_text: {summarize(entity.get('chunk_text'))}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
