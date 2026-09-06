"""Build the Medical RAG V2 hybrid collection from the frozen V1 collection."""

from __future__ import annotations

import argparse
import hashlib
import importlib.metadata
import json
import re
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from pymilvus import DataType, Function, FunctionType, MilvusClient

from rag.embedding_provider import EmbeddingProvider
from rag.rag_config import (
    EMBEDDING_MODEL_NAME,
    MILVUS_COLLECTION_NAME,
    MILVUS_HOST,
    MILVUS_PORT,
    RAG_BM25_ANALYZER,
    RAG_DENSE_FIELD,
    RAG_HYBRID_COLLECTION_NAME,
    RAG_RRF_K,
    RAG_SPARSE_FIELD,
    RAG_TEXT_FIELD,
    milvus_uri,
)
from rag.rag_schema import MILVUS_OUTPUT_FIELDS


PROJECT_ROOT = Path(__file__).resolve().parents[2]
ARTIFACT_ROOT = PROJECT_ROOT / "rag" / "eval" / "artifacts"
EVAL_ROOT = PROJECT_ROOT / "rag" / "eval"
ANALYZER_INPUTS = ["胸闷", "冒冷汗", "胸痛伴大汗", "孕32周头痛眼花", "ALT升高", "HbA1c", "利伐沙班", "呼吸困难"]
CHINESE_RE = re.compile(r"[\u4e00-\u9fff]")


def git_commit_sha() -> str | None:
    try:
        result = subprocess.run(["git", "rev-parse", "HEAD"], cwd=PROJECT_ROOT, check=True, capture_output=True, text=True)
    except Exception:
        return None
    return result.stdout.strip() or None


def read_all_rows(client: MilvusClient, collection_name: str, output_fields: list[str]) -> list[dict[str, Any]]:
    iterator = client.query_iterator(
        collection_name=collection_name,
        batch_size=1000,
        limit=-1,
        filter="",
        output_fields=output_fields,
    )
    rows: list[dict[str, Any]] = []
    try:
        while True:
            batch = iterator.next()
            if not batch:
                break
            rows.extend(batch)
    finally:
        iterator.close()
    return rows


def create_hybrid_collection(client: MilvusClient, collection_name: str, embedding_dim: int, analyzer: str) -> None:
    schema = client.create_schema(auto_id=False, enable_dynamic_field=False)
    schema.add_field("chunk_id", DataType.VARCHAR, is_primary=True, max_length=128)
    schema.add_field("doc_id", DataType.VARCHAR, max_length=128)
    schema.add_field("doc_type", DataType.VARCHAR, max_length=64)
    schema.add_field("title", DataType.VARCHAR, max_length=512)
    schema.add_field("version", DataType.VARCHAR, max_length=32)
    schema.add_field("language", DataType.VARCHAR, max_length=32)
    schema.add_field("source_type", DataType.VARCHAR, max_length=128)
    schema.add_field("applicable_population", DataType.VARCHAR, max_length=1024)
    schema.add_field("related_symptoms", DataType.VARCHAR, max_length=1024)
    schema.add_field("related_departments", DataType.VARCHAR, max_length=1024)
    schema.add_field("urgency_level", DataType.VARCHAR, max_length=64)
    schema.add_field("content_json", DataType.VARCHAR, max_length=16000)
    schema.add_field(RAG_TEXT_FIELD, DataType.VARCHAR, max_length=16000, enable_analyzer=True, analyzer_params={"tokenizer": analyzer})
    schema.add_field(RAG_DENSE_FIELD, DataType.FLOAT_VECTOR, dim=embedding_dim)
    schema.add_field(RAG_SPARSE_FIELD, DataType.SPARSE_FLOAT_VECTOR)
    schema.add_function(
        Function(
            name="chunk_text_bm25",
            function_type=FunctionType.BM25,
            input_field_names=[RAG_TEXT_FIELD],
            output_field_names=[RAG_SPARSE_FIELD],
        )
    )

    index_params = client.prepare_index_params()
    try:
        index_params.add_index(RAG_DENSE_FIELD, index_type="AUTOINDEX", metric_type="COSINE")
    except Exception:
        index_params.add_index(
            RAG_DENSE_FIELD,
            index_type="HNSW",
            metric_type="COSINE",
            params={"M": 16, "efConstruction": 200},
        )
    index_params.add_index(RAG_SPARSE_FIELD, index_type="SPARSE_INVERTED_INDEX", metric_type="BM25", params={})
    client.create_collection(collection_name=collection_name, schema=schema, index_params=index_params)


def text_hash(value: Any) -> str:
    return hashlib.sha256(str(value or "").encode("utf-8")).hexdigest()


def parity_check(v1_rows: list[dict[str, Any]], v2_rows: list[dict[str, Any]]) -> dict[str, Any]:
    v1_by_id = {row.get("chunk_id"): row for row in v1_rows if row.get("chunk_id")}
    v2_by_id = {row.get("chunk_id"): row for row in v2_rows if row.get("chunk_id")}
    missing = sorted(set(v1_by_id) - set(v2_by_id))
    extra = sorted(set(v2_by_id) - set(v1_by_id))
    mismatched = [
        chunk_id
        for chunk_id in sorted(set(v1_by_id) & set(v2_by_id))
        if text_hash(v1_by_id[chunk_id].get(RAG_TEXT_FIELD)) != text_hash(v2_by_id[chunk_id].get(RAG_TEXT_FIELD))
    ]
    return {
        "v1_chunks": len(v1_by_id),
        "v2_chunks": len(v2_by_id),
        "missing_in_v2": missing,
        "extra_in_v2": extra,
        "chunk_text_hash_mismatch": mismatched,
    }


def analyzer_check(client: MilvusClient, analyzer: str) -> dict[str, Any]:
    rows = []
    whole_phrase_failures = []
    for text in ANALYZER_INPUTS:
        tokens_raw = client.run_analyzer(text, analyzer_params={"tokenizer": analyzer})
        tokens_value = getattr(tokens_raw, "tokens", tokens_raw)
        tokens = [str(item) for item in tokens_value]
        if len(text) >= 5 and CHINESE_RE.search(text) and tokens == [text]:
            whole_phrase_failures.append(text)
        rows.append({"input": text, "tokens": tokens})
    return {
        "analyzer": {"tokenizer": analyzer},
        "inputs": rows,
        "sanity_passed": not whole_phrase_failures,
        "whole_phrase_failures": whole_phrase_failures,
    }


def collection_manifest(
    client: MilvusClient,
    v1_collection: str,
    v2_collection: str,
    analyzer_result: dict[str, Any],
    parity: dict[str, Any],
) -> dict[str, Any]:
    schema_v1 = json.loads(json.dumps(to_jsonable(client.describe_collection(v1_collection)), ensure_ascii=False))
    schema_v2 = json.loads(json.dumps(to_jsonable(client.describe_collection(v2_collection)), ensure_ascii=False))
    return {
        "rag_version": "medical_rag_v2_hybrid_rrf",
        "created_at": datetime.now(timezone.utc).isoformat(),
        "git_commit_sha": git_commit_sha(),
        "python_version": sys.version,
        "dependencies": {
            "pymilvus": importlib.metadata.version("pymilvus"),
        },
        "milvus": {
            "uri": milvus_uri(MILVUS_HOST, MILVUS_PORT),
            "server_version": client.get_server_version(),
        },
        "dense": {
            "embedding_model": EMBEDDING_MODEL_NAME,
            "metric": "COSINE",
            "index": "AUTOINDEX with HNSW fallback",
            "field": RAG_DENSE_FIELD,
        },
        "sparse": {
            "engine": "milvus_bm25",
            "analyzer": analyzer_result["analyzer"],
            "text_field": RAG_TEXT_FIELD,
            "sparse_field": RAG_SPARSE_FIELD,
        },
        "fusion": {
            "method": "RRF",
            "rrf_k": RAG_RRF_K,
        },
        "queries": {
            "dense_query": "expanded_query",
            "sparse_query": "original_query",
        },
        "reranker_enabled": False,
        "parent_child_enabled": False,
        "query_rewrite_enabled": False,
        "collection_v1": v1_collection,
        "collection_v2": v2_collection,
        "schema_v1": schema_v1,
        "schema_v2": schema_v2,
        "analyzer_check": analyzer_result,
        "corpus_parity_check": parity,
    }


def to_jsonable(value: Any) -> Any:
    if isinstance(value, dict):
        return {str(key): to_jsonable(item) for key, item in value.items()}
    if isinstance(value, (list, tuple, set)):
        return [to_jsonable(item) for item in value]
    if isinstance(value, (str, int, float, bool)) or value is None:
        return value
    try:
        return [to_jsonable(item) for item in value]
    except TypeError:
        return str(value)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--v1-collection", default=MILVUS_COLLECTION_NAME)
    parser.add_argument("--v2-collection", default=RAG_HYBRID_COLLECTION_NAME)
    parser.add_argument("--analyzer", default=RAG_BM25_ANALYZER)
    parser.add_argument("--reset-v2", action="store_true")
    args = parser.parse_args()

    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    output_dir = ARTIFACT_ROOT / f"hybrid_collection_build_{timestamp}"
    output_dir.mkdir(parents=True, exist_ok=False)

    client = MilvusClient(uri=milvus_uri(MILVUS_HOST, MILVUS_PORT))
    if not client.has_collection(args.v1_collection):
        raise SystemExit(f"V1 collection does not exist: {args.v1_collection}")
    analyzer_result = analyzer_check(client, args.analyzer)
    (output_dir / "bm25_analyzer_check.json").write_text(json.dumps(analyzer_result, ensure_ascii=False, indent=2), encoding="utf-8")

    v1_fields = [*MILVUS_OUTPUT_FIELDS, RAG_DENSE_FIELD]
    v1_rows = read_all_rows(client, args.v1_collection, v1_fields)
    if not v1_rows:
        raise SystemExit("V1 collection has no rows; cannot build V2")
    embedding_dim = len(v1_rows[0].get(RAG_DENSE_FIELD) or [])
    if not embedding_dim:
        embedding_dim = EmbeddingProvider().embedding_dim

    if client.has_collection(args.v2_collection):
        if not args.reset_v2:
            raise SystemExit(f"V2 collection already exists: {args.v2_collection}. Re-run with --reset-v2 to rebuild it.")
        client.drop_collection(args.v2_collection)
    create_hybrid_collection(client, args.v2_collection, embedding_dim, args.analyzer)
    insert_rows = [{key: row.get(key) for key in v1_fields} for row in v1_rows]
    result = client.insert(collection_name=args.v2_collection, data=insert_rows)
    client.flush(args.v2_collection)
    client.load_collection(args.v2_collection)
    inserted = int(result.get("insert_count", len(insert_rows))) if isinstance(result, dict) else len(insert_rows)

    v2_rows = read_all_rows(client, args.v2_collection, MILVUS_OUTPUT_FIELDS)
    parity = parity_check(v1_rows, v2_rows)
    manifest = collection_manifest(client, args.v1_collection, args.v2_collection, analyzer_result, parity)
    manifest["inserted_count"] = inserted
    (output_dir / "corpus_parity_check.json").write_text(json.dumps(parity, ensure_ascii=False, indent=2), encoding="utf-8")
    (output_dir / "medical_rag_v2_hybrid_manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    (EVAL_ROOT / "medical_rag_v2_hybrid_manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    (EVAL_ROOT / "bm25_analyzer_check.json").write_text(json.dumps(analyzer_result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"artifact_path": str(output_dir), "inserted_count": inserted, "corpus_parity_check": parity}, ensure_ascii=False, indent=2))
    return 0 if analyzer_result["sanity_passed"] and not parity["missing_in_v2"] and not parity["extra_in_v2"] and not parity["chunk_text_hash_mismatch"] else 2


if __name__ == "__main__":
    raise SystemExit(main())
