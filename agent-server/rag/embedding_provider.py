"""Embedding provider abstraction for local medical RAG ingestion."""

from __future__ import annotations

import os
from typing import Sequence

from .rag_config import EMBEDDING_MODEL_NAME, EMBEDDING_PROVIDER


class EmbeddingProvider:
    def __init__(self, provider: str = EMBEDDING_PROVIDER, model_name: str = EMBEDDING_MODEL_NAME):
        if provider != "local_sentence_transformers":
            raise ValueError(
                f"Unsupported EMBEDDING_PROVIDER={provider!r}. 当前阶段仅支持 local_sentence_transformers。"
            )
        self.provider = provider
        self.model_name = model_name
        local_files_only = os.getenv("RAG_EMBEDDING_LOCAL_ONLY", "true").lower() != "false"
        if local_files_only:
            os.environ.setdefault("HF_HUB_OFFLINE", "1")
            os.environ.setdefault("TRANSFORMERS_OFFLINE", "1")

        try:
            from sentence_transformers import SentenceTransformer
        except ImportError as exc:
            raise RuntimeError(
                "无法加载 embedding 模型：未安装 sentence-transformers。请先执行 pip install -r requirements-rag.txt。"
            ) from exc
        except Exception as exc:
            raise RuntimeError(
                "无法导入 sentence-transformers，可能是 PyTorch/transformers 版本不兼容。"
                " 请执行 pip install -r requirements-rag.txt，并确认 PyTorch 可用。"
            ) from exc

        try:
            self.model = SentenceTransformer(model_name)
        except Exception as exc:  # pragma: no cover - depends on local model/network
            raise RuntimeError(
                "无法加载 embedding 模型，请先安装 sentence-transformers 并确保模型可下载或已缓存到本地。"
                f" 模型名: {model_name}"
            ) from exc

        probe = self.encode_texts(["维度探测"])
        self.embedding_dim = len(probe[0]) if probe else 0
        print(f"Embedding model loaded: {self.model_name}, dim={self.embedding_dim}")

    def encode_texts(self, texts: Sequence[str], batch_size: int = 32) -> list[list[float]]:
        vectors = self.model.encode(
            list(texts),
            batch_size=batch_size,
            normalize_embeddings=True,
            show_progress_bar=False,
        )
        return [vector.astype("float32").tolist() for vector in vectors]
