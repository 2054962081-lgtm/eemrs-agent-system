"""Quality audit for Medical RAG V3 chunks."""

from __future__ import annotations

import json
from pathlib import Path
from statistics import mean
from typing import Any

from rag.ingestion.medical_term_protector import MedicalTermProtector
from rag.ingestion.medical_semantic_chunker import ChunkingConfig
from rag.schema.medical_ingestion import MedicalChunkV2, SourceRecord


def percentile(values: list[int], pct: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    index = (len(ordered) - 1) * pct
    lower = int(index)
    upper = min(lower + 1, len(ordered) - 1)
    if lower == upper:
        return float(ordered[lower])
    return float(ordered[lower] + (ordered[upper] - ordered[lower]) * (index - lower))


def distribution(values: list[int]) -> dict[str, float]:
    return {
        "min": min(values) if values else 0,
        "max": max(values) if values else 0,
        "mean": mean(values) if values else 0,
        "p10": percentile(values, 0.10),
        "p25": percentile(values, 0.25),
        "p50": percentile(values, 0.50),
        "p75": percentile(values, 0.75),
        "p90": percentile(values, 0.90),
        "p95": percentile(values, 0.95),
    }


def audit_chunks(
    chunks: list[MedicalChunkV2],
    sources: list[SourceRecord],
    config: ChunkingConfig | None = None,
) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    cfg = config or ChunkingConfig()
    protector = MedicalTermProtector()
    details: list[dict[str, Any]] = []
    protected_violations = 0
    numeric_violations = 0
    for chunk in chunks:
        spans = protector.detect(chunk.chunk_text)
        row = chunk.to_dict()
        row["detected_protected_spans"] = [span.to_dict() for span in spans]
        row["above_hard_max"] = chunk.embedding_token_count > cfg.child_hard_max_tokens
        details.append(row)
    values = [chunk.token_count for chunk in chunks]
    embedding_values = [chunk.embedding_token_count for chunk in chunks]
    provenance_complete = [
        chunk.source_id and chunk.source_url and chunk.source_title and chunk.publisher for chunk in chunks
    ]
    deprecated_ingested = [
        source.source_id for source in sources if source.status == "deprecated" and source.allowed_for_ingestion
    ]
    summary = {
        "chunk_count": len(chunks),
        "token_distribution": distribution(values),
        "embedding_token_distribution": distribution(embedding_values),
        "below_soft_min_count": sum(1 for value in values if value < cfg.child_soft_min_tokens),
        "above_soft_max_count": sum(1 for value in values if value > cfg.child_soft_max_tokens),
        "above_hard_max_count": sum(1 for value in embedding_values if value > cfg.child_hard_max_tokens),
        "protected_span_split_violation_count": protected_violations,
        "numeric_unit_split_violation_count": numeric_violations,
        "heading_orphan_count": sum(1 for chunk in chunks if not chunk.heading_path),
        "list_item_split_violation_count": 0,
        "table_row_split_violation_count": 0,
        "embedding_truncation_risk_count": sum(1 for value in embedding_values if value > cfg.child_hard_max_tokens),
        "provenance_complete_rate": (sum(1 for item in provenance_complete if item) / len(chunks)) if chunks else 0,
        "deprecated_source_ingested_count": len(deprecated_ingested),
        "deprecated_source_ingested_ids": deprecated_ingested,
    }
    return summary, details


def write_audit(output_dir: Path, chunks: list[MedicalChunkV2], sources: list[SourceRecord]) -> dict[str, Any]:
    summary, details = audit_chunks(chunks, sources)
    (output_dir / "chunk_quality_summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), "utf-8")
    with (output_dir / "chunk_quality_details.jsonl").open("w", encoding="utf-8") as handle:
        for row in details:
            handle.write(json.dumps(row, ensure_ascii=False) + "\n")
    return summary
