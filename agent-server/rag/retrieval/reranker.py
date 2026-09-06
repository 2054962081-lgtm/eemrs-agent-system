"""Cross-encoder reranking for fused Medical RAG candidates."""

from __future__ import annotations

import os
import threading
from dataclasses import dataclass
from typing import Any, Protocol, Sequence

from rag.rag_config import (
    RAG_RERANKER_BATCH_SIZE,
    RAG_RERANKER_DEVICE,
    RAG_RERANKER_LOCAL_ONLY,
    RAG_RERANKER_MAX_LENGTH,
    RAG_RERANKER_MODEL_NAME,
    RAG_RERANKER_MODEL_REVISION,
)

from .fusion import FusedCandidate


class PairScorer(Protocol):
    def predict(self, pairs: list[list[str]], batch_size: int) -> list[float]:
        ...


@dataclass(frozen=True)
class RerankerInfo:
    model: str
    revision: str
    device: str
    max_length: int
    batch_size: int
    local_files_only: bool
    loaded: bool


class CrossEncoderPairScorer:
    def __init__(
        self,
        model_name: str = RAG_RERANKER_MODEL_NAME,
        revision: str = RAG_RERANKER_MODEL_REVISION,
        device: str = RAG_RERANKER_DEVICE,
        max_length: int = RAG_RERANKER_MAX_LENGTH,
        local_files_only: bool = RAG_RERANKER_LOCAL_ONLY,
    ):
        if local_files_only:
            os.environ["HF_HUB_OFFLINE"] = "1"
            os.environ["TRANSFORMERS_OFFLINE"] = "1"
        resolved_device = resolve_device(device)
        try:
            from sentence_transformers import CrossEncoder
        except ImportError as exc:
            raise RuntimeError("无法加载 reranker：未安装 sentence-transformers。") from exc
        self.model_name = model_name
        self.revision = revision
        self.device = resolved_device
        self.max_length = max_length
        self.local_files_only = local_files_only
        self.model = CrossEncoder(
            model_name,
            max_length=max_length,
            device=resolved_device,
            revision=revision,
            tokenizer_args={"local_files_only": local_files_only},
            automodel_args={"local_files_only": local_files_only},
        )

    def predict(self, pairs: list[list[str]], batch_size: int) -> list[float]:
        scores = self.model.predict(
            pairs,
            batch_size=batch_size,
            show_progress_bar=False,
            convert_to_numpy=True,
        )
        return [float(score) for score in scores.tolist()]

    def info(self, batch_size: int = RAG_RERANKER_BATCH_SIZE) -> RerankerInfo:
        return RerankerInfo(
            model=self.model_name,
            revision=self.revision,
            device=self.device,
            max_length=self.max_length,
            batch_size=batch_size,
            local_files_only=self.local_files_only,
            loaded=True,
        )


class CrossEncoderReranker:
    def __init__(self, scorer: PairScorer, batch_size: int = RAG_RERANKER_BATCH_SIZE):
        self.scorer = scorer
        self.batch_size = batch_size

    def rerank(
        self,
        query: str,
        candidates: Sequence[FusedCandidate],
        top_n: int | None = None,
    ) -> list[FusedCandidate]:
        if not candidates:
            return []
        pairs = [[query, str(candidate.entity.get("chunk_text") or "")] for candidate in candidates]
        scores = self.scorer.predict(pairs, batch_size=self.batch_size)
        if len(scores) != len(candidates):
            raise RuntimeError(f"reranker score count mismatch: scores={len(scores)}, candidates={len(candidates)}")

        enriched: list[FusedCandidate] = []
        for rrf_rank, (candidate, score) in enumerate(zip(candidates, scores), 1):
            entity = dict(candidate.entity)
            entity["_rrf_rank"] = rrf_rank
            entity["_rerank_score"] = float(score)
            enriched.append(
                FusedCandidate(
                    chunk_id=candidate.chunk_id,
                    entity=entity,
                    rrf_score=candidate.rrf_score,
                    dense_rank=candidate.dense_rank,
                    dense_score=candidate.dense_score,
                    sparse_rank=candidate.sparse_rank,
                    sparse_score=candidate.sparse_score,
                    retrieval_channels=candidate.retrieval_channels,
                )
            )
        enriched.sort(
            key=lambda item: (
                -float(item.entity.get("_rerank_score", 0.0)),
                int(item.entity.get("_rrf_rank") or 10**9),
                item.chunk_id,
            )
        )
        reranked: list[FusedCandidate] = []
        for rerank_rank, candidate in enumerate(enriched[:top_n], 1):
            entity = dict(candidate.entity)
            entity["_rerank_rank"] = rerank_rank
            reranked.append(
                FusedCandidate(
                    chunk_id=candidate.chunk_id,
                    entity=entity,
                    rrf_score=candidate.rrf_score,
                    dense_rank=candidate.dense_rank,
                    dense_score=candidate.dense_score,
                    sparse_rank=candidate.sparse_rank,
                    sparse_score=candidate.sparse_score,
                    retrieval_channels=candidate.retrieval_channels,
                )
            )
        return reranked


_DEFAULT_RERANKER: CrossEncoderReranker | None = None
_DEFAULT_SCORER: CrossEncoderPairScorer | None = None
_RERANKER_LOCK = threading.Lock()


def resolve_device(device: str) -> str:
    if device != "auto":
        return device
    try:
        import torch
    except Exception:
        return "cpu"
    return "cuda" if torch.cuda.is_available() else "cpu"


def get_default_reranker() -> CrossEncoderReranker:
    global _DEFAULT_RERANKER, _DEFAULT_SCORER
    if _DEFAULT_RERANKER is not None:
        return _DEFAULT_RERANKER
    with _RERANKER_LOCK:
        if _DEFAULT_RERANKER is None:
            _DEFAULT_SCORER = CrossEncoderPairScorer()
            _DEFAULT_RERANKER = CrossEncoderReranker(_DEFAULT_SCORER, batch_size=RAG_RERANKER_BATCH_SIZE)
    return _DEFAULT_RERANKER


def default_reranker_info() -> dict[str, Any]:
    if _DEFAULT_SCORER is None:
        return {
            "model": RAG_RERANKER_MODEL_NAME,
            "revision": RAG_RERANKER_MODEL_REVISION,
            "device": resolve_device(RAG_RERANKER_DEVICE),
            "max_length": RAG_RERANKER_MAX_LENGTH,
            "batch_size": RAG_RERANKER_BATCH_SIZE,
            "local_files_only": RAG_RERANKER_LOCAL_ONLY,
            "loaded": False,
        }
    return _DEFAULT_SCORER.info(batch_size=RAG_RERANKER_BATCH_SIZE).__dict__
