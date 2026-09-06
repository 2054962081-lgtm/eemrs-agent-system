"""Audit V1 internal knowledge chunk lengths with the configured BGE tokenizer."""

from __future__ import annotations

import argparse
import json
from datetime import datetime
from pathlib import Path

from rag.build_chunks import build_chunks
from rag.ingestion.chunk_quality_audit import distribution
from rag.ingestion.tokenizer_utils import BgeTokenizer
from rag.rag_config import CHUNK_OVERLAP, CHUNK_SPLIT_THRESHOLD, EMBEDDING_MODEL_NAME, resolve_project_path
from rag.validate_rag_knowledge import validate_knowledge_dir


def run(output_dir: Path | None = None) -> Path:
    tokenizer = BgeTokenizer(EMBEDDING_MODEL_NAME)
    validation = validate_knowledge_dir(resolve_project_path("rag_knowledge"))
    built = build_chunks(validation.documents)
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    out = output_dir or resolve_project_path("rag/eval/artifacts") / f"chunking_v1_audit_{timestamp}"
    out.mkdir(parents=True, exist_ok=True)
    details = []
    for chunk in built.chunks:
        token_length = tokenizer.count(chunk["chunk_text"])
        details.append(
            {
                "chunk_id": chunk["chunk_id"],
                "doc_id": chunk["doc_id"],
                "doc_type": chunk["doc_type"],
                "title": chunk["title"],
                "char_length": len(chunk["chunk_text"]),
                "token_length_by_bge_tokenizer": token_length,
            }
        )
    token_values = [row["token_length_by_bge_tokenizer"] for row in details]
    char_values = [row["char_length"] for row in details]
    summary = {
        "embedding_model": EMBEDDING_MODEL_NAME,
        "tokenizer": tokenizer.info.__dict__,
        "chunk_split_threshold_chars": CHUNK_SPLIT_THRESHOLD,
        "chunk_overlap_chars": CHUNK_OVERLAP,
        "chunk_count": len(details),
        "char_length_distribution": distribution(char_values),
        "token_length_distribution": distribution(token_values),
        "token_length_gt_512_count": sum(1 for value in token_values if value > 512),
        "token_length_gt_480_count": sum(1 for value in token_values if value > 480),
        "token_length_lt_80_count": sum(1 for value in token_values if value < 80),
        "truncation_risk": any(value > tokenizer.model_max_length for value in token_values),
    }
    (out / "chunk_length_distribution.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), "utf-8")
    with (out / "chunk_length_details.jsonl").open("w", encoding="utf-8") as handle:
        for row in details:
            handle.write(json.dumps(row, ensure_ascii=False) + "\n")
    report = [
        "# Chunking V1 Audit",
        "",
        f"- tokenizer: {summary['tokenizer']}",
        f"- chunk count: {len(details)}",
        f"- threshold chars: {CHUNK_SPLIT_THRESHOLD}",
        f"- overlap chars: {CHUNK_OVERLAP}",
        f"- token distribution: {summary['token_length_distribution']}",
        f"- token_length > 512 count: {summary['token_length_gt_512_count']}",
        f"- token_length > 480 count: {summary['token_length_gt_480_count']}",
        f"- token_length < 80 count: {summary['token_length_lt_80_count']}",
        f"- BGE truncation risk: {summary['truncation_risk']}",
    ]
    (out / "report.md").write_text("\n".join(report), "utf-8")
    return out


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-dir")
    args = parser.parse_args()
    print(run(Path(args.output_dir) if args.output_dir else None))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
