"""Build the Medical RAG V3 external evidence child collection."""

from __future__ import annotations

import argparse
import hashlib
import importlib.metadata
import json
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from pymilvus import DataType, Function, FunctionType, MilvusClient

from rag.embedding_provider import EmbeddingProvider
from rag.rag_config import (
    EMBEDDING_MODEL_NAME,
    MEDICAL_CHILD_HARD_MAX_TOKENS,
    MEDICAL_RAG_V3_COLLECTION_NAME,
    MILVUS_HOST,
    MILVUS_PORT,
    PROJECT_ROOT,
    RAG_BM25_ANALYZER,
    RAG_DENSE_FIELD,
    RAG_RRF_K,
    RAG_SPARSE_FIELD,
    RAG_TEXT_FIELD,
    milvus_uri,
)
from rag.store.parent_store import read_jsonl, resolve_parent_child_paths


EXTERNAL_DOC_TYPE = "external_evidence"
ARTIFACT_ROOT = PROJECT_ROOT / "rag" / "eval" / "artifacts"
EXTERNAL_OUTPUT_FIELDS = [
    "chunk_id",
    "parent_id",
    "source_id",
    "knowledge_origin",
    "source_title",
    "publisher",
    "publication_date",
    "version",
    "document_type",
    "source_status",
    "license_status",
    "source_url",
    "heading_path",
    "page_start",
    "page_end",
    "doc_id",
    "doc_type",
    "title",
    "language",
    "source_type",
    "applicable_population",
    "related_symptoms",
    "related_departments",
    "urgency_level",
    "content_json",
    RAG_TEXT_FIELD,
    "embedding_text",
    "token_count",
    "embedding_token_count",
]


def git_commit_sha() -> str | None:
    try:
        result = subprocess.run(["git", "rev-parse", "HEAD"], cwd=PROJECT_ROOT, check=True, capture_output=True, text=True)
    except Exception:
        return None
    return result.stdout.strip() or None


def latest_cleaned_artifact() -> Path:
    candidates = sorted(ARTIFACT_ROOT.glob("medical_rag_v3_2_production_integration_*"), key=lambda path: path.name, reverse=True)
    for candidate in candidates:
        if (candidate / "staging" / "cleaned_semantic_chunks.jsonl").exists():
            return candidate
    raise FileNotFoundError("No V3.2 cleaned staging artifact found")


def text_hash(value: Any) -> str:
    return hashlib.sha256(str(value or "").encode("utf-8")).hexdigest()


def load_sources(artifact_dir: Path) -> dict[str, dict[str, Any]]:
    for path in [artifact_dir / "source_manifest.json", artifact_dir / "source_cleaning" / "source_manifest.json"]:
        if path.exists():
            data = json.loads(path.read_text(encoding="utf-8"))
            return {row["source_id"]: row for row in data.get("sources", [])}
    return {}


def load_parent_child_rows(artifact_dir: Path) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    parent_path, child_path = resolve_parent_child_paths(artifact_dir)
    return read_jsonl(parent_path), read_jsonl(child_path)


def production_gate(
    parents: list[dict[str, Any]],
    chunks: list[dict[str, Any]],
    sources: dict[str, dict[str, Any]],
    chunk_quality: dict[str, Any] | None,
) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    parent_ids = {row.get("parent_id") for row in parents}
    accepted = []
    rejected = []
    quality_pass = bool(
        chunk_quality
        and chunk_quality.get("embedding_truncation_risk_count") == 0
        and chunk_quality.get("protected_span_split_violation_count") == 0
        and chunk_quality.get("numeric_unit_split_violation_count") == 0
        and chunk_quality.get("heading_orphan_count") == 0
    )
    for chunk in chunks:
        source = sources.get(chunk.get("source_id"), {})
        reasons = []
        if source.get("status") != "active":
            reasons.append("SOURCE_NOT_ACTIVE")
        if source.get("allowed_for_ingestion") is not True:
            reasons.append("SOURCE_NOT_ALLOWED")
        if bool(source.get("deprecated")):
            reasons.append("SOURCE_DEPRECATED")
        if source.get("parser_status") not in {None, "parsed"}:
            reasons.append("PARSER_NOT_OK")
        if not quality_pass:
            reasons.append("CHUNK_QUALITY_NOT_PASS")
        if int(chunk.get("embedding_token_count") or 0) > MEDICAL_CHILD_HARD_MAX_TOKENS:
            reasons.append("EMBEDDING_TOKEN_HARD_MAX_EXCEEDED")
        if not all(chunk.get(key) for key in ["source_id", "source_title", "publisher", "source_url", "license_status"]):
            reasons.append("INCOMPLETE_SOURCE_PROVENANCE")
        if chunk.get("parent_id") not in parent_ids:
            reasons.append("INVALID_PARENT_REF")
        if reasons:
            rejected.append({"chunk_id": chunk.get("chunk_id"), "source_id": chunk.get("source_id"), "reasons": reasons})
        else:
            accepted.append(chunk)
    return accepted, rejected


def create_external_collection(client: MilvusClient, collection_name: str, embedding_dim: int, analyzer: str) -> None:
    schema = client.create_schema(auto_id=False, enable_dynamic_field=False)
    schema.add_field("chunk_id", DataType.VARCHAR, is_primary=True, max_length=160)
    schema.add_field("parent_id", DataType.VARCHAR, max_length=160)
    schema.add_field("source_id", DataType.VARCHAR, max_length=160)
    schema.add_field("knowledge_origin", DataType.VARCHAR, max_length=64)
    schema.add_field("source_title", DataType.VARCHAR, max_length=512)
    schema.add_field("publisher", DataType.VARCHAR, max_length=256)
    schema.add_field("publication_date", DataType.VARCHAR, max_length=64)
    schema.add_field("version", DataType.VARCHAR, max_length=64)
    schema.add_field("document_type", DataType.VARCHAR, max_length=128)
    schema.add_field("source_status", DataType.VARCHAR, max_length=64)
    schema.add_field("license_status", DataType.VARCHAR, max_length=128)
    schema.add_field("source_url", DataType.VARCHAR, max_length=1024)
    schema.add_field("heading_path", DataType.VARCHAR, max_length=2048)
    schema.add_field("page_start", DataType.INT64)
    schema.add_field("page_end", DataType.INT64)
    schema.add_field("doc_id", DataType.VARCHAR, max_length=160)
    schema.add_field("doc_type", DataType.VARCHAR, max_length=64)
    schema.add_field("title", DataType.VARCHAR, max_length=512)
    schema.add_field("language", DataType.VARCHAR, max_length=32)
    schema.add_field("source_type", DataType.VARCHAR, max_length=128)
    schema.add_field("applicable_population", DataType.VARCHAR, max_length=1024)
    schema.add_field("related_symptoms", DataType.VARCHAR, max_length=1024)
    schema.add_field("related_departments", DataType.VARCHAR, max_length=1024)
    schema.add_field("urgency_level", DataType.VARCHAR, max_length=64)
    schema.add_field("content_json", DataType.VARCHAR, max_length=16000)
    schema.add_field(RAG_TEXT_FIELD, DataType.VARCHAR, max_length=16000, enable_analyzer=True, analyzer_params={"tokenizer": analyzer})
    schema.add_field("embedding_text", DataType.VARCHAR, max_length=16000)
    schema.add_field("token_count", DataType.INT64)
    schema.add_field("embedding_token_count", DataType.INT64)
    schema.add_field(RAG_DENSE_FIELD, DataType.FLOAT_VECTOR, dim=embedding_dim)
    schema.add_field(RAG_SPARSE_FIELD, DataType.SPARSE_FLOAT_VECTOR)
    schema.add_function(
        Function(
            name="external_chunk_text_bm25",
            function_type=FunctionType.BM25,
            input_field_names=[RAG_TEXT_FIELD],
            output_field_names=[RAG_SPARSE_FIELD],
        )
    )
    index_params = client.prepare_index_params()
    try:
        index_params.add_index(RAG_DENSE_FIELD, index_type="AUTOINDEX", metric_type="COSINE")
    except Exception:
        index_params.add_index(RAG_DENSE_FIELD, index_type="HNSW", metric_type="COSINE", params={"M": 16, "efConstruction": 200})
    index_params.add_index(RAG_SPARSE_FIELD, index_type="SPARSE_INVERTED_INDEX", metric_type="BM25", params={})
    client.create_collection(collection_name=collection_name, schema=schema, index_params=index_params)


def row_for_insert(chunk: dict[str, Any], source: dict[str, Any], embedding: list[float]) -> dict[str, Any]:
    heading_path = chunk.get("heading_path") or []
    content_json = {
        "source_id": chunk.get("source_id"),
        "parent_id": chunk.get("parent_id"),
        "document_type": chunk.get("document_type"),
        "heading_path": heading_path,
        "page_start": chunk.get("page_start"),
        "page_end": chunk.get("page_end"),
    }
    topic_text = " ".join([chunk.get("source_title") or "", " ".join(heading_path), chunk.get("chunk_text") or ""])
    return {
        "chunk_id": chunk["chunk_id"],
        "parent_id": chunk["parent_id"],
        "source_id": chunk["source_id"],
        "knowledge_origin": "external_evidence",
        "source_title": chunk.get("source_title") or source.get("title") or "",
        "publisher": chunk.get("publisher") or source.get("publisher") or "",
        "publication_date": chunk.get("publication_date") or source.get("publication_date") or "",
        "version": chunk.get("version") or source.get("version") or "",
        "document_type": chunk.get("document_type") or source.get("document_type") or "",
        "source_status": chunk.get("source_status") or source.get("status") or "",
        "license_status": chunk.get("license_status") or source.get("license_status") or "",
        "source_url": chunk.get("source_url") or source.get("source_url") or "",
        "heading_path": " > ".join(heading_path),
        "page_start": int(chunk.get("page_start") or 0),
        "page_end": int(chunk.get("page_end") or 0),
        "doc_id": chunk.get("source_id") or "",
        "doc_type": EXTERNAL_DOC_TYPE,
        "title": chunk.get("source_title") or source.get("title") or "",
        "language": source.get("language") or "zh",
        "source_type": "authoritative_external_evidence",
        "applicable_population": infer_population(topic_text),
        "related_symptoms": infer_symptoms(topic_text),
        "related_departments": "",
        "urgency_level": "",
        "content_json": json.dumps(content_json, ensure_ascii=False),
        RAG_TEXT_FIELD: chunk.get("chunk_text") or "",
        "embedding_text": chunk.get("embedding_text") or chunk.get("chunk_text") or "",
        "token_count": int(chunk.get("token_count") or 0),
        "embedding_token_count": int(chunk.get("embedding_token_count") or 0),
        RAG_DENSE_FIELD: embedding,
    }


def infer_population(text: str) -> str:
    labels = []
    if any(term in text for term in ["妊娠", "孕", "先兆子痫"]):
        labels.append("孕妇")
    if "成人" in text:
        labels.append("成人")
    return "；".join(labels)


def infer_symptoms(text: str) -> str:
    terms = [term for term in ["高血压", "血压", "卒中", "头痛", "视觉障碍", "蛋白尿", "胸痛", "言语不清"] if term in text]
    return "；".join(dict.fromkeys(terms))


def read_all_rows(client: MilvusClient, collection_name: str) -> list[dict[str, Any]]:
    iterator = client.query_iterator(collection_name=collection_name, batch_size=1000, limit=-1, filter="", output_fields=EXTERNAL_OUTPUT_FIELDS)
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


def parity_check(chunks: list[dict[str, Any]], rows: list[dict[str, Any]]) -> dict[str, Any]:
    staging = {row["chunk_id"]: row for row in chunks}
    milvus = {row["chunk_id"]: row for row in rows}
    common = sorted(set(staging) & set(milvus))
    return {
        "staging_child_count": len(staging),
        "milvus_child_count": len(milvus),
        "missing_chunk_ids": sorted(set(staging) - set(milvus)),
        "extra_chunk_ids": sorted(set(milvus) - set(staging)),
        "text_hash_mismatch": [
            chunk_id for chunk_id in common if text_hash(staging[chunk_id].get("chunk_text")) != text_hash(milvus[chunk_id].get(RAG_TEXT_FIELD))
        ],
        "parent_id_mismatch": [
            chunk_id for chunk_id in common if str(staging[chunk_id].get("parent_id")) != str(milvus[chunk_id].get("parent_id"))
        ],
        "source_id_mismatch": [
            chunk_id for chunk_id in common if str(staging[chunk_id].get("source_id")) != str(milvus[chunk_id].get("source_id"))
        ],
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


def build_collection(args: argparse.Namespace) -> dict[str, Any]:
    artifact_dir = Path(args.artifact_path) if args.artifact_path else latest_cleaned_artifact()
    output_dir = Path(args.output_dir) if args.output_dir else artifact_dir / "collection"
    output_dir.mkdir(parents=True, exist_ok=True)
    parents, chunks = load_parent_child_rows(artifact_dir / "staging" if (artifact_dir / "staging").exists() else artifact_dir)
    sources = load_sources(artifact_dir)
    quality_path = artifact_dir / "staging" / "chunk_quality_summary.json"
    chunk_quality = json.loads(quality_path.read_text(encoding="utf-8")) if quality_path.exists() else None
    accepted, rejected = production_gate(parents, chunks, sources, chunk_quality)
    gate = {"accepted_count": len(accepted), "rejected_count": len(rejected), "rejections": rejected}
    (output_dir / "production_gate.json").write_text(json.dumps(gate, ensure_ascii=False, indent=2), encoding="utf-8")

    manifest = {
        "rag_version": "medical_rag_v3_2_external_evidence_child_collection",
        "created_at": datetime.now(timezone.utc).isoformat(),
        "git_commit_sha": git_commit_sha(),
        "collection_name": args.collection_name,
        "dry_run": bool(args.dry_run),
        "reset_requested": bool(args.reset),
        "embedding_model": EMBEDDING_MODEL_NAME,
        "embedding_input": "embedding_text",
        "bm25": {"engine": "milvus_bm25", "analyzer": RAG_BM25_ANALYZER, "text_field": RAG_TEXT_FIELD, "sparse_field": RAG_SPARSE_FIELD},
        "fusion": {"method": "RRF", "rrf_k": RAG_RRF_K},
        "parent_vector_search": False,
        "production_gate": gate,
    }
    if args.dry_run:
        health = {"status": "DRY_RUN_ONLY", "inserted_count": 0, "collection_loaded": False}
        parity = parity_check(accepted, [])
        manifest["schema"] = {"fields": EXTERNAL_OUTPUT_FIELDS + [RAG_DENSE_FIELD, RAG_SPARSE_FIELD]}
        write_collection_outputs(output_dir, manifest, parity, health)
        return {"artifact_path": str(output_dir), "manifest": manifest, "parity": parity, "health": health}

    client = MilvusClient(uri=milvus_uri(MILVUS_HOST, MILVUS_PORT))
    provider = EmbeddingProvider()
    if client.has_collection(args.collection_name):
        if args.reset:
            client.drop_collection(args.collection_name)
        else:
            description = client.describe_collection(args.collection_name)
            manifest["existing_schema"] = to_jsonable(description)
    if not client.has_collection(args.collection_name):
        create_external_collection(client, args.collection_name, provider.embedding_dim, RAG_BM25_ANALYZER)
    existing = set()
    if accepted:
        quoted = ", ".join(json.dumps(row["chunk_id"], ensure_ascii=False) for row in accepted)
        existing_rows = client.query(args.collection_name, filter=f"chunk_id in [{quoted}]", output_fields=["chunk_id"])
        existing = {row["chunk_id"] for row in existing_rows}
    to_insert = [row for row in accepted if row["chunk_id"] not in existing]
    embeddings = provider.encode_texts([row.get("embedding_text") or row.get("chunk_text") or "" for row in to_insert], batch_size=args.batch_size)
    rows = [row_for_insert(chunk, sources.get(chunk.get("source_id"), {}), embedding) for chunk, embedding in zip(to_insert, embeddings)]
    inserted = 0
    if rows:
        result = client.insert(collection_name=args.collection_name, data=rows)
        inserted = int(result.get("insert_count", len(rows))) if isinstance(result, dict) else len(rows)
    client.flush(args.collection_name)
    client.load_collection(args.collection_name)
    rows_after = read_all_rows(client, args.collection_name)
    parity = parity_check(accepted, rows_after)
    manifest["schema"] = to_jsonable(client.describe_collection(args.collection_name))
    manifest["inserted_count"] = inserted
    manifest["skipped_existing_count"] = len(existing)
    health = {
        "status": "OK",
        "collection_loaded": True,
        "row_count": len(rows_after),
        "server_version": client.get_server_version(),
        "pymilvus": importlib.metadata.version("pymilvus"),
        "python_version": sys.version,
    }
    write_collection_outputs(output_dir, manifest, parity, health)
    return {"artifact_path": str(output_dir), "manifest": manifest, "parity": parity, "health": health}


def write_collection_outputs(output_dir: Path, manifest: dict[str, Any], parity: dict[str, Any], health: dict[str, Any]) -> None:
    (output_dir / "collection_manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    (output_dir / "collection_parity.json").write_text(json.dumps(parity, ensure_ascii=False, indent=2), encoding="utf-8")
    (output_dir / "collection_health.json").write_text(json.dumps(health, ensure_ascii=False, indent=2), encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--artifact-path")
    parser.add_argument("--output-dir")
    parser.add_argument("--collection-name", default=MEDICAL_RAG_V3_COLLECTION_NAME)
    parser.add_argument("--batch-size", type=int, default=32)
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--reset", action="store_true")
    args = parser.parse_args()
    result = build_collection(args)
    print(json.dumps(result, ensure_ascii=False, indent=2))
    parity = result["parity"]
    ok = not parity["missing_chunk_ids"] and not parity["extra_chunk_ids"] and not parity["text_hash_mismatch"]
    if args.dry_run:
        return 0
    return 0 if ok and result["health"]["status"] == "OK" else 2


if __name__ == "__main__":
    raise SystemExit(main())
