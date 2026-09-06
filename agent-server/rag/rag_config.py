"""Configuration for first-stage medical RAG ingestion."""

from __future__ import annotations

import os
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]

MILVUS_HOST = os.getenv("MILVUS_HOST", "localhost")
MILVUS_PORT = os.getenv("MILVUS_PORT", "19530")
MILVUS_COLLECTION_NAME = os.getenv("MILVUS_COLLECTION_NAME", "medical_rag_chunks")
RAG_HYBRID_COLLECTION_NAME = os.getenv("RAG_HYBRID_COLLECTION_NAME", f"{MILVUS_COLLECTION_NAME}_v2")
USER_MEMORY_COLLECTION_NAME = os.getenv("USER_MEMORY_COLLECTION_NAME", "medical_user_memory")

KNOWLEDGE_BASE_DIR = os.getenv("KNOWLEDGE_BASE_DIR", "rag_knowledge")
DEFAULT_TOP_K = int(os.getenv("RAG_DEFAULT_TOP_K", "5"))
RAG_RETRIEVAL_MODE = os.getenv("RAG_RETRIEVAL_MODE", "dense").strip().lower()
RAG_RRF_K = int(os.getenv("RAG_RRF_K", "60"))
RAG_DENSE_FIELD = os.getenv("RAG_DENSE_FIELD", "embedding")
RAG_TEXT_FIELD = os.getenv("RAG_TEXT_FIELD", "chunk_text")
RAG_SPARSE_FIELD = os.getenv("RAG_SPARSE_FIELD", "sparse_embedding")
RAG_BM25_ANALYZER = os.getenv("RAG_BM25_ANALYZER", "jieba")
RAG_RERANKER_MODEL_NAME = os.getenv("RAG_RERANKER_MODEL_NAME", "cross-encoder/mmarco-mMiniLMv2-L12-H384-v1")
RAG_RERANKER_MODEL_REVISION = os.getenv("RAG_RERANKER_MODEL_REVISION", "main")
RAG_RERANKER_DEVICE = os.getenv("RAG_RERANKER_DEVICE", "auto")
RAG_RERANKER_MAX_LENGTH = int(os.getenv("RAG_RERANKER_MAX_LENGTH", "512"))
RAG_RERANKER_BATCH_SIZE = int(os.getenv("RAG_RERANKER_BATCH_SIZE", "16"))
RAG_RERANKER_LOCAL_ONLY = os.getenv("RAG_RERANKER_LOCAL_ONLY", "true").lower() != "false"

EMBEDDING_PROVIDER = os.getenv("EMBEDDING_PROVIDER", "local_sentence_transformers")
EMBEDDING_MODEL_NAME = os.getenv("EMBEDDING_MODEL_NAME", "BAAI/bge-small-zh-v1.5")

MAX_CONTENT_JSON_LENGTH = int(os.getenv("RAG_MAX_CONTENT_JSON_LENGTH", "16000"))
MAX_CHUNK_TEXT_LENGTH = int(os.getenv("RAG_MAX_CHUNK_TEXT_LENGTH", "16000"))
CHUNK_SPLIT_THRESHOLD = int(os.getenv("RAG_CHUNK_SPLIT_THRESHOLD", "1500"))
CHUNK_OVERLAP = int(os.getenv("RAG_CHUNK_OVERLAP", "80"))

MEDICAL_CHILD_TARGET_TOKENS = int(os.getenv("MEDICAL_CHILD_TARGET_TOKENS", "320"))
MEDICAL_CHILD_SOFT_MIN_TOKENS = int(os.getenv("MEDICAL_CHILD_SOFT_MIN_TOKENS", "120"))
MEDICAL_CHILD_SOFT_MAX_TOKENS = int(os.getenv("MEDICAL_CHILD_SOFT_MAX_TOKENS", "420"))
MEDICAL_CHILD_HARD_MAX_TOKENS = int(os.getenv("MEDICAL_CHILD_HARD_MAX_TOKENS", "480"))
MEDICAL_RAG_V3_COLLECTION_NAME = os.getenv("MEDICAL_RAG_V3_COLLECTION_NAME", "medical_rag_external_v3")
RAG_EXTERNAL_EVIDENCE_ENABLED = os.getenv("RAG_EXTERNAL_EVIDENCE_ENABLED", "false").strip().lower() == "true"
MEDICAL_RAG_V3_PARENT_ARTIFACT_PATH = os.getenv("MEDICAL_RAG_V3_PARENT_ARTIFACT_PATH", "").strip()
RAG_CONTEXT_MODE = os.getenv("RAG_CONTEXT_MODE", "legacy").strip().lower()
RAG_CONTEXT_MAX_TOKENS = int(os.getenv("RAG_CONTEXT_MAX_TOKENS", "1800"))
RAG_TOTAL_MODEL_CONTEXT_LIMIT = int(os.getenv("RAG_TOTAL_MODEL_CONTEXT_LIMIT", "8192"))
RAG_RESERVED_NON_RAG_TOKENS = int(os.getenv("RAG_RESERVED_NON_RAG_TOKENS", "6400"))
PARENT_CHILD_NEIGHBOR_BEFORE = int(os.getenv("PARENT_CHILD_NEIGHBOR_BEFORE", "1"))
PARENT_CHILD_NEIGHBOR_AFTER = int(os.getenv("PARENT_CHILD_NEIGHBOR_AFTER", "1"))
CONTEXT_PARENT_MAX_TOKENS = int(os.getenv("CONTEXT_PARENT_MAX_TOKENS", "700"))
MAX_CONTEXT_UNITS_PER_PARENT = int(os.getenv("MAX_CONTEXT_UNITS_PER_PARENT", "1"))
MAX_CONTEXT_UNITS_PER_SOURCE = int(os.getenv("MAX_CONTEXT_UNITS_PER_SOURCE", "4"))


def resolve_project_path(path_value: str | Path) -> Path:
    path = Path(path_value)
    if path.is_absolute():
        return path
    return PROJECT_ROOT / path


def milvus_uri(host: str = MILVUS_HOST, port: str = MILVUS_PORT) -> str:
    return f"http://{host}:{port}"
