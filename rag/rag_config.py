"""Configuration for first-stage medical RAG ingestion."""

from __future__ import annotations

import os
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]

MILVUS_HOST = os.getenv("MILVUS_HOST", "localhost")
MILVUS_PORT = os.getenv("MILVUS_PORT", "19530")
MILVUS_COLLECTION_NAME = os.getenv("MILVUS_COLLECTION_NAME", "medical_rag_chunks")

KNOWLEDGE_BASE_DIR = os.getenv("KNOWLEDGE_BASE_DIR", "rag_knowledge")
DEFAULT_TOP_K = int(os.getenv("RAG_DEFAULT_TOP_K", "5"))

EMBEDDING_PROVIDER = os.getenv("EMBEDDING_PROVIDER", "local_sentence_transformers")
EMBEDDING_MODEL_NAME = os.getenv("EMBEDDING_MODEL_NAME", "BAAI/bge-small-zh-v1.5")

MAX_CONTENT_JSON_LENGTH = int(os.getenv("RAG_MAX_CONTENT_JSON_LENGTH", "16000"))
MAX_CHUNK_TEXT_LENGTH = int(os.getenv("RAG_MAX_CHUNK_TEXT_LENGTH", "16000"))
CHUNK_SPLIT_THRESHOLD = int(os.getenv("RAG_CHUNK_SPLIT_THRESHOLD", "1500"))
CHUNK_OVERLAP = int(os.getenv("RAG_CHUNK_OVERLAP", "80"))


def resolve_project_path(path_value: str | Path) -> Path:
    path = Path(path_value)
    if path.is_absolute():
        return path
    return PROJECT_ROOT / path


def milvus_uri(host: str = MILVUS_HOST, port: str = MILVUS_PORT) -> str:
    return f"http://{host}:{port}"
