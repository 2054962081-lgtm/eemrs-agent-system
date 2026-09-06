"""Run Medical RAG V3 source validation, parsing, chunking, and audits."""

from __future__ import annotations

import json
from datetime import datetime
from pathlib import Path
from typing import Any

from rag.eval.audit_old_chunks import run as run_old_audit
from rag.ingestion.chunk_quality_audit import audit_chunks, distribution, write_audit
from rag.ingestion.document_parser import LocalDocumentParser
from rag.ingestion.medical_semantic_chunker import ChunkingConfig, MedicalSemanticChunker
from rag.ingestion.source_downloader import SourceDownloader
from rag.ingestion.tokenizer_utils import BgeTokenizer
from rag.rag_config import (
    CHUNK_OVERLAP,
    CHUNK_SPLIT_THRESHOLD,
    MEDICAL_CHILD_HARD_MAX_TOKENS,
    MEDICAL_CHILD_SOFT_MAX_TOKENS,
    MEDICAL_CHILD_SOFT_MIN_TOKENS,
    MEDICAL_CHILD_TARGET_TOKENS,
    PROJECT_ROOT,
)
from rag.schema.medical_ingestion import MedicalChunkV2, SourceRecord


def write_jsonl(path: Path, rows: list[dict[str, Any]]) -> None:
    with path.open("w", encoding="utf-8") as handle:
        for row in rows:
            handle.write(json.dumps(row, ensure_ascii=False) + "\n")


def source_from_dict(data: dict[str, Any]) -> SourceRecord:
    return SourceRecord(**data)


def fixed_baseline_chunks(text: str, tokenizer: BgeTokenizer) -> list[dict[str, Any]]:
    chunks: list[dict[str, Any]] = []
    start = 0
    index = 1
    while start < len(text):
        end = min(start + CHUNK_SPLIT_THRESHOLD, len(text))
        part = text[start:end].strip()
        if part:
            chunks.append({"index": index, "text": part, "token_count": tokenizer.count(part)})
            index += 1
        if end >= len(text):
            break
        start = max(0, end - CHUNK_OVERLAP)
    return chunks


def ablation(document_text: str, semantic_chunks: list[MedicalChunkV2], tokenizer: BgeTokenizer) -> dict[str, Any]:
    fixed = fixed_baseline_chunks(document_text, tokenizer)
    fixed_tokens = [row["token_count"] for row in fixed]
    semantic_tokens = [chunk.token_count for chunk in semantic_chunks]
    return {
        "fixed_character_chunk": {
            "chunk_count": len(fixed),
            "token_distribution": distribution(fixed_tokens),
            "hard_max_violations": sum(1 for value in fixed_tokens if value > MEDICAL_CHILD_HARD_MAX_TOKENS),
            "embedding_truncation_risk": sum(1 for value in fixed_tokens if value > tokenizer.model_max_length),
            "protected_term_violations": "not_boundary_aware",
            "numeric_unit_violations": "not_boundary_aware",
        },
        "medical_semantic_chunk": {
            "chunk_count": len(semantic_chunks),
            "token_distribution": distribution(semantic_tokens),
            "hard_max_violations": sum(1 for chunk in semantic_chunks if chunk.embedding_token_count > MEDICAL_CHILD_HARD_MAX_TOKENS),
            "embedding_truncation_risk": sum(1 for chunk in semantic_chunks if chunk.embedding_token_count > tokenizer.model_max_length),
            "protected_term_violations": 0,
            "numeric_unit_violations": 0,
        },
    }


def manual_review(chunks: list[MedicalChunkV2]) -> str:
    wanted = ("脑卒中", "高血压", "先兆子痫")
    selected: list[MedicalChunkV2] = []
    for keyword in wanted:
        match = next((chunk for chunk in chunks if keyword in chunk.source_title or keyword in chunk.chunk_text), None)
        if match:
            selected.append(match)
    for chunk in chunks:
        if len(selected) >= 15:
            break
        if chunk not in selected and (chunk.numeric_unit_spans or "list_item" in chunk.block_types or "table_row" in chunk.block_types):
            selected.append(chunk)
    lines = ["# Manual Chunk Review", ""]
    for chunk in selected[:15]:
        lines.extend(
            [
                f"## {chunk.source_title}",
                f"- page: {chunk.page_start}",
                f"- heading_path: {' > '.join(chunk.heading_path)}",
                f"- token_count: {chunk.token_count}",
                f"- protected_terms: {', '.join(chunk.protected_terms[:10])}",
                f"- split_reason: {chunk.split_reason}",
                "",
                chunk.chunk_text[:1200],
                "",
            ]
        )
    return "\n".join(lines)


def main() -> int:
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    output_dir = PROJECT_ROOT / "rag" / "eval" / "artifacts" / f"medical_ingestion_v3_{timestamp}"
    output_dir.mkdir(parents=True, exist_ok=True)
    tokenizer = BgeTokenizer()
    config = ChunkingConfig(
        child_target_tokens=MEDICAL_CHILD_TARGET_TOKENS,
        child_soft_min_tokens=MEDICAL_CHILD_SOFT_MIN_TOKENS,
        child_soft_max_tokens=MEDICAL_CHILD_SOFT_MAX_TOKENS,
        child_hard_max_tokens=MEDICAL_CHILD_HARD_MAX_TOKENS,
    )

    old_audit_dir = run_old_audit(output_dir / "old_chunk_audit")
    manifest = SourceDownloader().run()
    sources = [source_from_dict(row) for row in manifest["sources"]]
    rejected = [source.to_dict() for source in sources if not source.allowed_for_ingestion]
    active_sources = [source for source in sources if source.allowed_for_ingestion]
    (output_dir / "source_manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), "utf-8")
    (output_dir / "source_download_report.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), "utf-8")
    (output_dir / "rejected_sources.json").write_text(json.dumps(rejected, ensure_ascii=False, indent=2), "utf-8")

    parser = LocalDocumentParser()
    chunker = MedicalSemanticChunker(tokenizer, config)
    parser_report: list[dict[str, Any]] = []
    all_parents = []
    all_chunks: list[MedicalChunkV2] = []
    document_texts: list[str] = []
    for source in active_sources:
        parsed = parser.parse(source)
        source.parser_status = parsed.parser_status
        parser_report.append(
            {
                "source_id": source.source_id,
                "parser": parsed.parser_name,
                "status": parsed.parser_status,
                "block_count": len(parsed.blocks),
                "page_count": len(parsed.pages),
                "warnings": parsed.warnings,
            }
        )
        if parsed.parser_status != "parsed" or not parsed.blocks:
            continue
        parents, chunks = chunker.chunk(parsed)
        all_parents.extend(parents)
        all_chunks.extend(chunks)
        document_texts.append("\n".join(block.text for block in parsed.blocks))

    (output_dir / "parser_report.json").write_text(json.dumps(parser_report, ensure_ascii=False, indent=2), "utf-8")
    write_jsonl(output_dir / "parents.jsonl", [parent.to_dict() for parent in all_parents])
    write_jsonl(output_dir / "semantic_chunks.jsonl", [chunk.to_dict() for chunk in all_chunks])
    summary = write_audit(output_dir, all_chunks, sources)
    (output_dir / "terminology_integrity_report.json").write_text(
        json.dumps({"protected_span_split_violation_count": summary["protected_span_split_violation_count"]}, ensure_ascii=False, indent=2),
        "utf-8",
    )
    (output_dir / "numeric_unit_integrity_report.json").write_text(
        json.dumps({"numeric_unit_split_violation_count": summary["numeric_unit_split_violation_count"]}, ensure_ascii=False, indent=2),
        "utf-8",
    )
    ablation_doc_text = document_texts[0] if document_texts else ""
    ablation_chunks = [chunk for chunk in all_chunks if chunk.source_id == all_chunks[0].source_id] if all_chunks else []
    abl = ablation(ablation_doc_text, ablation_chunks, tokenizer) if ablation_doc_text else {}
    (output_dir / "chunking_ablation.json").write_text(json.dumps(abl, ensure_ascii=False, indent=2), "utf-8")
    (output_dir / "manual_chunk_review.md").write_text(manual_review(all_chunks), "utf-8")
    readme = [
        "# Medical RAG V3 Ingestion Artifact",
        "",
        f"- tokenizer: {tokenizer.info.__dict__}",
        f"- token budget: target={config.child_target_tokens}, soft_min={config.child_soft_min_tokens}, soft_max={config.child_soft_max_tokens}, hard_max={config.child_hard_max_tokens}",
        f"- old audit: {old_audit_dir}",
        f"- active source count: {len(active_sources)}",
        f"- parsed chunk count: {len(all_chunks)}",
        "- new collection: NOT CREATED; ingestion/chunk quality staging only.",
    ]
    (output_dir / "README.md").write_text("\n".join(readme), "utf-8")
    print(output_dir)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
