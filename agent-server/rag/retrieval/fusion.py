"""Rank-based fusion utilities for hybrid medical retrieval."""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any


@dataclass(frozen=True)
class RankedCandidate:
    chunk_id: str
    rank: int
    score: float | None
    entity: dict[str, Any] = field(default_factory=dict)


@dataclass(frozen=True)
class FusedCandidate:
    chunk_id: str
    entity: dict[str, Any]
    rrf_score: float
    dense_rank: int | None = None
    dense_score: float | None = None
    sparse_rank: int | None = None
    sparse_score: float | None = None
    retrieval_channels: tuple[str, ...] = ()


def fuse_rrf(
    dense_candidates: list[RankedCandidate],
    sparse_candidates: list[RankedCandidate],
    rrf_k: int = 60,
) -> list[FusedCandidate]:
    if rrf_k <= 0:
        raise ValueError("rrf_k must be positive")

    merged: dict[str, dict[str, Any]] = {}
    for channel, candidates in (("dense", dense_candidates), ("bm25", sparse_candidates)):
        for candidate in candidates:
            if not candidate.chunk_id:
                continue
            item = merged.setdefault(
                candidate.chunk_id,
                {
                    "chunk_id": candidate.chunk_id,
                    "entity": dict(candidate.entity),
                    "rrf_score": 0.0,
                    "channels": [],
                },
            )
            if not item["entity"] and candidate.entity:
                item["entity"] = dict(candidate.entity)
            if channel == "dense":
                item["dense_rank"] = candidate.rank
                item["dense_score"] = candidate.score
                if candidate.entity:
                    item["entity"] = dict(candidate.entity)
            else:
                item["sparse_rank"] = candidate.rank
                item["sparse_score"] = candidate.score
                if not item["entity"] and candidate.entity:
                    item["entity"] = dict(candidate.entity)
            item["rrf_score"] += 1.0 / (rrf_k + candidate.rank)
            item["channels"].append(channel)

    fused = [
        FusedCandidate(
            chunk_id=item["chunk_id"],
            entity=item["entity"],
            rrf_score=float(item["rrf_score"]),
            dense_rank=item.get("dense_rank"),
            dense_score=item.get("dense_score"),
            sparse_rank=item.get("sparse_rank"),
            sparse_score=item.get("sparse_score"),
            retrieval_channels=tuple(dict.fromkeys(item["channels"])),
        )
        for item in merged.values()
    ]
    fused.sort(
        key=lambda item: (
            -item.rrf_score,
            item.dense_rank if item.dense_rank is not None else 10**9,
            item.sparse_rank if item.sparse_rank is not None else 10**9,
            item.chunk_id,
        )
    )
    return fused


def fused_candidate_trace(candidate: FusedCandidate, rank: int | None = None) -> dict[str, Any]:
    row: dict[str, Any] = {
        "chunk_id": candidate.chunk_id,
        "doc_id": candidate.entity.get("doc_id"),
        "doc_type": candidate.entity.get("doc_type"),
        "title": candidate.entity.get("title"),
        "dense_rank": candidate.dense_rank,
        "dense_score": candidate.dense_score,
        "sparse_rank": candidate.sparse_rank,
        "sparse_score": candidate.sparse_score,
        "rrf_score": candidate.rrf_score,
        "retrieval_channels": list(candidate.retrieval_channels),
    }
    if rank is not None:
        row["rank"] = rank
    return row
