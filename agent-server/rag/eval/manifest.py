"""Build a Medical RAG V1 baseline manifest from current code/config."""

from __future__ import annotations

import importlib.metadata
import json
import platform
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from rag import rag_api_server
from rag.rag_config import (
    CHUNK_OVERLAP,
    CHUNK_SPLIT_THRESHOLD,
    DEFAULT_TOP_K,
    EMBEDDING_MODEL_NAME,
    EMBEDDING_PROVIDER,
    MILVUS_COLLECTION_NAME,
    MILVUS_HOST,
    MILVUS_PORT,
)


PROJECT_ROOT = Path(__file__).resolve().parents[2]


DEPENDENCIES = [
    "pymilvus",
    "sentence-transformers",
    "transformers",
    "numpy",
    "fastapi",
    "uvicorn",
    "pydantic",
]


def git_commit_sha() -> str | None:
    try:
        result = subprocess.run(
            ["git", "rev-parse", "HEAD"],
            cwd=PROJECT_ROOT,
            check=True,
            capture_output=True,
            text=True,
        )
    except Exception:
        return None
    return result.stdout.strip() or None


def dependency_versions() -> dict[str, str | None]:
    versions: dict[str, str | None] = {}
    for name in DEPENDENCIES:
        try:
            versions[name] = importlib.metadata.version(name)
        except importlib.metadata.PackageNotFoundError:
            versions[name] = None
    return versions


def collection_metadata() -> dict[str, Any]:
    metadata: dict[str, Any] = {
        "collection_name": MILVUS_COLLECTION_NAME,
        "host": MILVUS_HOST,
        "port": MILVUS_PORT,
        "available": False,
        "vector_dimension": None,
        "description": None,
        "error": None,
    }
    try:
        from rag.milvus_client import MedicalRagMilvus

        client = MedicalRagMilvus(MILVUS_HOST, MILVUS_PORT, MILVUS_COLLECTION_NAME)
        client.connect()
        metadata["available"] = client.has_collection()
        metadata["vector_dimension"] = client.embedding_dim()
        if metadata["available"]:
            metadata["description"] = client.client.describe_collection(client.collection_name)
    except Exception as exc:
        metadata["error"] = str(exc)
    return metadata


def embedding_dimension() -> dict[str, Any]:
    try:
        from rag.embedding_provider import EmbeddingProvider

        provider = EmbeddingProvider()
        return {"value": provider.embedding_dim, "source": "EmbeddingProvider runtime probe", "error": None}
    except Exception as exc:
        return {"value": None, "source": None, "error": str(exc)}


def build_manifest(run_id: str, include_live_collection: bool = True) -> dict[str, Any]:
    milvus = collection_metadata() if include_live_collection else {
        "collection_name": MILVUS_COLLECTION_NAME,
        "host": MILVUS_HOST,
        "port": MILVUS_PORT,
        "available": None,
        "vector_dimension": None,
        "description": None,
        "error": "not checked",
    }
    embedding_dim = embedding_dimension()
    vector_dimension = milvus.get("vector_dimension") or embedding_dim["value"]
    return {
        "rag_version": "medical_rag_v1_dense",
        "retrieval_mode": "dense_only",
        "baseline_run_id": run_id,
        "run_timestamp": datetime.now(timezone.utc).isoformat(),
        "git_commit_sha": git_commit_sha(),
        "python_version": sys.version,
        "python_implementation": platform.python_implementation(),
        "dependencies": dependency_versions(),
        "embedding_provider": EMBEDDING_PROVIDER,
        "embedding_model": EMBEDDING_MODEL_NAME,
        "vector_store": "milvus",
        "milvus_collection_name": MILVUS_COLLECTION_NAME,
        "milvus": milvus,
        "vector_dimension": vector_dimension,
        "embedding_dimension": embedding_dim,
        "metric_type": "COSINE",
        "index_type": "AUTOINDEX with HNSW fallback",
        "index_params": {
            "primary": {"index_type": "AUTOINDEX", "metric_type": "COSINE"},
            "fallback": {"index_type": "HNSW", "metric_type": "COSINE", "params": {"M": 16, "efConstruction": 200}},
        },
        "search_params": {"metric_type": "COSINE", "params": {}},
        "chunk_size": CHUNK_SPLIT_THRESHOLD,
        "chunk_overlap": CHUNK_OVERLAP,
        "default_top_k": DEFAULT_TOP_K,
        "agent_default_top_k": 8,
        "agent_deep_or_medical_record_min_top_k": 10,
        "scene_quotas": {scene: dict(rag_api_server.quota_for_scene(scene)) for scene in ["pre_inquiry", "deep_inquiry", "medical_record"]},
        "default_doc_types": list(rag_api_server.DEFAULT_DOC_TYPES),
        "medical_record_doc_types": list(rag_api_server.MEDICAL_RECORD_DOC_TYPES),
        "query_expansion_enabled": True,
        "reranker_enabled": False,
        "sparse_retrieval_enabled": False,
        "fusion_enabled": False,
        "parent_child_enabled": False,
        "normal_api_contract": {
            "method": "POST",
            "path": "/rag/retrieve",
            "request_model": "RetrieveRequest",
            "response_model": "RetrieveResponse",
            "unchanged_by_baseline_runner": True,
        },
    }


def write_manifest(path: Path, run_id: str, include_live_collection: bool = True) -> dict[str, Any]:
    manifest = build_manifest(run_id, include_live_collection=include_live_collection)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    return manifest
