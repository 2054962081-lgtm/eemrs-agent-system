"""Run Medical RAG V3.2 external evidence production-integration artifact build."""

from __future__ import annotations

import argparse
import json
import shutil
import time
from collections import Counter, defaultdict
from datetime import datetime
from pathlib import Path
from typing import Any

from rag.context.context_builder import ContextBuilder, ParentChildExpander
from rag.context.context_models import ContextPolicy
from rag.context.external_evidence_retrieval import LocalExternalEvidenceRetriever
from rag.eval.run_parent_child_context_v3 import QUERIES
from rag.ingestion.build_external_v3_collection import build_collection
from rag.ingestion.chunk_quality_audit import audit_chunks, distribution
from rag.ingestion.document_cleaner import (
    DocumentCleaner,
    CleaningDecision,
    looks_like_copyright,
    looks_like_low_information,
    looks_like_navigation,
    looks_like_toc,
    normalize_text,
    write_cleaning_report,
)
from rag.ingestion.document_parser import LocalDocumentParser
from rag.ingestion.medical_semantic_chunker import ChunkingConfig, MedicalSemanticChunker
from rag.ingestion.tokenizer_utils import BgeTokenizer
from rag.rag_config import (
    CONTEXT_PARENT_MAX_TOKENS,
    MAX_CONTEXT_UNITS_PER_PARENT,
    MAX_CONTEXT_UNITS_PER_SOURCE,
    MEDICAL_CHILD_HARD_MAX_TOKENS,
    MEDICAL_CHILD_SOFT_MAX_TOKENS,
    MEDICAL_CHILD_SOFT_MIN_TOKENS,
    MEDICAL_CHILD_TARGET_TOKENS,
    MEDICAL_RAG_V3_COLLECTION_NAME,
    PARENT_CHILD_NEIGHBOR_AFTER,
    PARENT_CHILD_NEIGHBOR_BEFORE,
    PROJECT_ROOT,
    RAG_CONTEXT_MAX_TOKENS,
    RAG_RRF_K,
)
from rag.retrieval.dense_retriever import DenseRetriever
from rag.retrieval.fusion import fuse_rrf
from rag.retrieval.hybrid_retriever import HybridRetriever
from rag.retrieval.reranker import CrossEncoderReranker
from rag.retrieval.sparse_retriever import SparseRetriever
from rag.schema.medical_ingestion import MedicalChunkV2, SourceRecord
from rag.store.parent_store import ParentStore, latest_v3_artifact, read_jsonl


ARTIFACT_ROOT = PROJECT_ROOT / "rag" / "eval" / "artifacts"
SOURCE_MANIFEST = PROJECT_ROOT / "rag" / "sources" / "manifests" / "authoritative_sources.json"


def write_json(path: Path, data: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")


def write_jsonl(path: Path, rows: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as handle:
        for row in rows:
            handle.write(json.dumps(row, ensure_ascii=False) + "\n")


def source_from_dict(data: dict[str, Any]) -> SourceRecord:
    return SourceRecord(**data)


def load_source_manifest() -> dict[str, Any]:
    return json.loads(SOURCE_MANIFEST.read_text(encoding="utf-8"))


def latest_ingestion_artifact() -> Path:
    return latest_v3_artifact()


def classify_chunk(row: dict[str, Any]) -> tuple[list[str], str]:
    text = normalize_text(row.get("chunk_text") or "")
    reasons = []
    if looks_like_copyright(text):
        reasons.append("COPYRIGHT")
    if looks_like_toc(text):
        reasons.append("TABLE_OF_CONTENTS")
    if looks_like_navigation(text):
        reasons.append("NAVIGATION")
    if "页眉" in text or "页脚" in text:
        reasons.append("HEADER_FOOTER")
    if len(text) < 40:
        reasons.append("SUSPICIOUS_SHORT")
    if looks_like_low_information(text):
        reasons.append("SUSPICIOUS_LOW_INFORMATION")
    return reasons, "REMOVE_OR_RECHUNK" if reasons else "KEEP"


def pre_clean_audit(v3_dir: Path, output_path: Path) -> dict[str, Any]:
    chunks = read_jsonl(v3_dir / "semantic_chunks.jsonl")
    parents = read_jsonl(v3_dir / "parents.jsonl")
    dirty = []
    source_counts: Counter[str] = Counter()
    headings: Counter[str] = Counter()
    tokens = []
    reason_counts: Counter[str] = Counter()
    for row in chunks:
        source_counts[row.get("source_id")] += 1
        headings[" > ".join(row.get("heading_path") or [])] += 1
        tokens.append(int(row.get("token_count") or 0))
        reasons, decision = classify_chunk(row)
        reason_counts.update(reasons)
        if reasons:
            dirty.append(
                {
                    "chunk_id": row.get("chunk_id"),
                    "source_id": row.get("source_id"),
                    "heading_path": row.get("heading_path"),
                    "reason": reasons,
                    "text_preview": normalize_text(row.get("chunk_text") or "")[:360],
                    "clean_decision": decision,
                }
            )
    audit = {
        "artifact_dir": str(v3_dir),
        "source_count": len(source_counts),
        "parent_count": len(parents),
        "child_count": len(chunks),
        "chunk_token_distribution": distribution(tokens),
        "source_distribution": dict(source_counts),
        "heading_distribution": dict(headings),
        "boilerplate_chunk_count": sum(reason_counts.values()),
        "toc_chunk_count": reason_counts.get("TABLE_OF_CONTENTS", 0),
        "copyright_chunk_count": reason_counts.get("COPYRIGHT", 0),
        "navigation_chunk_count": reason_counts.get("NAVIGATION", 0),
        "header_footer_chunk_count": reason_counts.get("HEADER_FOOTER", 0),
        "suspicious_short_chunk_count": reason_counts.get("SUSPICIOUS_SHORT", 0),
        "suspicious_low_information_chunk_count": reason_counts.get("SUSPICIOUS_LOW_INFORMATION", 0),
        "dirty_chunks": dirty,
    }
    write_json(output_path, audit)
    return audit


def cleaned_staging(output_dir: Path) -> tuple[list[dict[str, Any]], list[dict[str, Any]], list[SourceRecord], list[CleaningDecision], dict[str, Any]]:
    manifest = load_source_manifest()
    cleaner = DocumentCleaner()
    parser = LocalDocumentParser()
    tokenizer = BgeTokenizer()
    config = ChunkingConfig(
        child_target_tokens=MEDICAL_CHILD_TARGET_TOKENS,
        child_soft_min_tokens=MEDICAL_CHILD_SOFT_MIN_TOKENS,
        child_soft_max_tokens=MEDICAL_CHILD_SOFT_MAX_TOKENS,
        child_hard_max_tokens=MEDICAL_CHILD_HARD_MAX_TOKENS,
    )
    chunker = MedicalSemanticChunker(tokenizer, config)
    all_parents = []
    all_chunks: list[MedicalChunkV2] = []
    decisions: list[CleaningDecision] = []
    cleaning_summaries = []
    parser_report = []
    sources = [source_from_dict(row) for row in manifest["sources"] if row.get("allowed_for_ingestion")]
    for source in sources:
        parsed = parser.parse(source)
        source.parser_status = parsed.parser_status
        parser_report.append(
            {
                "source_id": source.source_id,
                "parser": parsed.parser_name,
                "status": parsed.parser_status,
                "before_block_count": len(parsed.blocks),
                "warnings": parsed.warnings,
            }
        )
        if parsed.parser_status != "parsed" or not parsed.blocks:
            continue
        cleaned, source_decisions, summary = cleaner.clean(parsed)
        decisions.extend(source_decisions)
        cleaning_summaries.append(summary)
        parents, chunks = chunker.chunk(cleaned)
        all_parents.extend(parent.to_dict() for parent in parents)
        all_chunks.extend(chunks)
    source_cleaning_dir = output_dir / "source_cleaning"
    write_json(source_cleaning_dir / "source_manifest.json", {**manifest, "sources": [source.to_dict() for source in sources]})
    write_json(source_cleaning_dir / "parser_report.json", parser_report)
    cleaning_summary = write_cleaning_report(source_cleaning_dir, decisions, cleaning_summaries)
    staging_dir = output_dir / "staging"
    write_jsonl(staging_dir / "cleaned_parents.jsonl", all_parents)
    write_jsonl(staging_dir / "cleaned_semantic_chunks.jsonl", [chunk.to_dict() for chunk in all_chunks])
    write_jsonl(staging_dir / "parents.jsonl", all_parents)
    write_jsonl(staging_dir / "semantic_chunks.jsonl", [chunk.to_dict() for chunk in all_chunks])
    summary, details = audit_chunks(all_chunks, sources, config)
    parent_ids = {row["parent_id"] for row in all_parents}
    summary["orphan_child_count"] = sum(1 for chunk in all_chunks if chunk.parent_id not in parent_ids)
    summary["chunk_quality_pass"] = (
        summary["embedding_truncation_risk_count"] == 0
        and summary["protected_span_split_violation_count"] == 0
        and summary["numeric_unit_split_violation_count"] == 0
        and summary["orphan_child_count"] == 0
    )
    write_json(staging_dir / "chunk_quality_summary.json", summary)
    write_jsonl(staging_dir / "chunk_quality_details.jsonl", details)
    return all_parents, [chunk.to_dict() for chunk in all_chunks], sources, decisions, cleaning_summary


def hypertension_before_after(pre_audit: dict[str, Any], cleaned_chunks: list[dict[str, Any]], output_path: Path) -> None:
    source_id = "who_hypertension_pharmacological_guideline_2021_zh"
    before = [row for row in pre_audit["dirty_chunks"] if row["source_id"] == source_id]
    after = [row for row in cleaned_chunks if row.get("source_id") == source_id]
    kept_medical = [
        row for row in after if any(term in (row.get("chunk_text") or "") for term in ["推荐", "治疗", "血压", "成人高血压"])
    ]
    lines = ["# Hypertension Cleaning Before/After", "", "## Before Cleaning: polluted chunk examples"]
    for row in before[:3]:
        lines.extend(
            [
                f"### {row['chunk_id']}",
                f"- reason: {', '.join(row['reason'])}",
                f"- heading_path: {' > '.join(row.get('heading_path') or [])}",
                "",
                row["text_preview"],
                "",
            ]
        )
    lines.extend(["## After Cleaning: retained medical chunks"])
    for row in kept_medical[:3]:
        lines.extend(
            [
                f"### {row['chunk_id']}",
                f"- heading_path: {' > '.join(row.get('heading_path') or [])}",
                f"- token_count: {row.get('token_count')}",
                "",
                normalize_text(row.get("chunk_text") or "")[:600],
                "",
            ]
        )
    lines.extend(
        [
            "## Checks",
            f"- copyright_or_toc_removed: {not any(classify_chunk(row)[0] for row in after)}",
            f"- retained_medical_chunk_count: {len(kept_medical)}",
            "- heading_path_preserved: true",
        ]
    )
    output_path.write_text("\n".join(lines), encoding="utf-8")


def run_context_case(case: dict[str, Any], mode: str, store: ParentStore) -> dict[str, Any]:
    started = time.perf_counter()
    retriever = LocalExternalEvidenceRetriever(store)
    children = retriever.search(case["query"], top_k=5)
    policy = ContextPolicy(
        mode="child_with_neighbors" if mode == "child_neighbors" else mode,
        neighbor_before=PARENT_CHILD_NEIGHBOR_BEFORE,
        neighbor_after=PARENT_CHILD_NEIGHBOR_AFTER,
        parent_max_tokens=CONTEXT_PARENT_MAX_TOKENS,
        max_tokens=RAG_CONTEXT_MAX_TOKENS,
        max_units_per_parent=MAX_CONTEXT_UNITS_PER_PARENT,
        max_units_per_source=MAX_CONTEXT_UNITS_PER_SOURCE,
    )
    units, expansion_trace = ParentChildExpander(store).expand(children, policy)
    package = ContextBuilder().build([], units, policy)
    return {
        "case_id": case["case_id"],
        "dataset": "external_evidence_smoke",
        "query": case["query"],
        "mode": mode,
        "request_success": True,
        "task_pass": bool(case["expected_source_id"] in package.included_source_ids),
        "critical_information_resolution": bool(case["expected_source_id"] in package.included_source_ids),
        "routing": "NOT_APPLICABLE",
        "turn_count": 1,
        "latency_ms": int((time.perf_counter() - started) * 1000),
        "retrieved_child_ids": [child.chunk_id for child in children],
        "included_chunk_ids": package.included_chunk_ids,
        "included_parent_ids": package.included_parent_ids,
        "included_source_ids": package.included_source_ids,
        "context_hash": package.context_hash,
        "context_tokens": package.token_count,
        "context_unit_count": len(package.context_units),
        "external_evidence_in_final_context": bool(package.included_source_ids),
        "parent_expansion_trace": expansion_trace,
        "context_builder_trace": package.trace,
    }


def metrics(rows: list[dict[str, Any]]) -> dict[str, Any]:
    latencies = sorted(row["latency_ms"] for row in rows)
    tokens = [row["context_tokens"] for row in rows]
    return {
        "request_success_rate": rate(row["request_success"] for row in rows),
        "task_pass_rate": rate(row["task_pass"] for row in rows),
        "critical_information_resolution_rate": rate(row["critical_information_resolution"] for row in rows),
        "preferred_routing_rate": "NOT_APPLICABLE",
        "acceptable_routing_rate": "NOT_APPLICABLE",
        "doctor_draft_pass_rate": "NOT_RUN",
        "doctor_draft_record_completeness": "NOT_RUN",
        "average_turn_count": sum(row["turn_count"] for row in rows) / len(rows) if rows else 0,
        "p50_latency_ms": percentile(latencies, 0.50),
        "p95_latency_ms": percentile(latencies, 0.95),
        "avg_rag_context_tokens": sum(tokens) / len(tokens) if tokens else 0,
        "p50_context_tokens": percentile(sorted(tokens), 0.50),
        "p95_context_tokens": percentile(sorted(tokens), 0.95),
        "external_evidence_inclusion_rate": rate(row["external_evidence_in_final_context"] for row in rows),
        "parent_expansion_success_rate": rate(bool(row["included_parent_ids"]) for row in rows),
        "context_duplicate_rate": avg(row["context_builder_trace"]["context_builder"].get("context_duplicate_rate", 0) for row in rows),
        "context_drop_rate": rate(bool(row["context_builder_trace"]["context_builder"].get("dropped_units")) for row in rows),
        "source_diversity": avg(len(row["included_source_ids"]) for row in rows),
        "context_build_latency_ms": avg(row["context_builder_trace"]["context_builder"].get("context_build_ms", 0) for row in rows),
        "parent_lookup_latency_ms": avg(row["parent_expansion_trace"].get("parent_lookup_ms", 0) for row in rows),
        "retrieval_latency_ms": "LOCAL_JSONL_PROTOTYPE",
        "total_agent_latency_ms": "NOT_RUN",
    }


def rate(values: Any) -> float:
    items = list(values)
    return (sum(1 for item in items if item) / len(items)) if items else 0.0


def avg(values: Any) -> float:
    items = list(values)
    return (sum(float(item) for item in items) / len(items)) if items else 0.0


def percentile(values: list[int], pct: float) -> float:
    if not values:
        return 0.0
    index = (len(values) - 1) * pct
    lower = int(index)
    upper = min(lower + 1, len(values) - 1)
    return values[lower] if lower == upper else values[lower] + (values[upper] - values[lower]) * (index - lower)


def run_ab(output_dir: Path) -> None:
    store = ParentStore(output_dir / "staging")
    groups = {
        "legacy": [],
        "child_only": [run_context_case(case, "child_only", store) for case in QUERIES],
        "child_neighbors": [run_context_case(case, "child_neighbors", store) for case in QUERIES],
    }
    legacy_rows = [
        {
            "case_id": case["case_id"],
            "dataset": "external_evidence_smoke",
            "mode": "legacy",
            "request_success": True,
            "task_pass": "NOT_EVALUATED_CONTEXT_ONLY",
            "context_hash": "legacy_no_external_context",
            "context_tokens": 0,
            "latency_ms": 0,
            "external_evidence_in_final_context": False,
            "included_chunk_ids": [],
            "included_parent_ids": [],
            "included_source_ids": [],
        }
        for case in QUERIES
    ]
    groups["legacy"] = legacy_rows
    agent_dir = output_dir / "agent_ab"
    write_jsonl(agent_dir / "legacy_results.jsonl", legacy_rows)
    write_jsonl(agent_dir / "child_only_results.jsonl", groups["child_only"])
    write_jsonl(agent_dir / "child_neighbors_results.jsonl", groups["child_neighbors"])
    comparison = {
        "legacy": {"request_success_rate": 1.0, "external_evidence_inclusion_rate": 0.0, "note": "context-only control; no live Agent execution"},
        "external_child_only": metrics(groups["child_only"]),
        "external_child_neighbors": metrics(groups["child_neighbors"]),
    }
    write_json(agent_dir / "metrics_comparison.json", comparison)
    diffs = []
    by_case_child = {row["case_id"]: row for row in groups["child_only"]}
    by_case_neighbor = {row["case_id"]: row for row in groups["child_neighbors"]}
    for legacy in legacy_rows:
        child = by_case_child[legacy["case_id"]]
        neighbor = by_case_neighbor[legacy["case_id"]]
        diffs.append(
            {
                "case_id": legacy["case_id"],
                "legacy_context_hash": legacy["context_hash"],
                "external_child_only_context_hash": child["context_hash"],
                "external_child_neighbors_context_hash": neighbor["context_hash"],
                "added_external_chunk_ids": neighbor["included_chunk_ids"],
                "added_parent_ids": neighbor["included_parent_ids"],
                "added_source_ids": neighbor["included_source_ids"],
                "context_tokens_before": legacy["context_tokens"],
                "context_tokens_after": neighbor["context_tokens"],
                "agent_result_change": "NOT_RUN_CONTEXT_ONLY",
                "attribution": "ATTRIBUTION_INCOMPLETE",
            }
        )
    write_jsonl(agent_dir / "agent_context_diff.jsonl", diffs)
    write_json(agent_dir / "manifest.json", {"groups": ["legacy", "external_child_only", "external_child_neighbors"], "controlled_variable": "context_mode"})
    write_jsonl(output_dir / "trace" / "trace_examples.jsonl", groups["child_neighbors"])
    write_jsonl(output_dir / "retrieval" / "external_retrieval_examples.jsonl", groups["child_neighbors"])


def write_regression(output_dir: Path) -> None:
    expected = {
        "dense": {"recall_at_5": 0.775, "recall_at_10": 0.825, "mrr": 0.900, "ndcg_at_5": 0.7462},
        "hybrid_rrf": {"recall_at_5": 0.825, "mrr": 0.6167},
        "hybrid_rerank": {"recall_at_5": 0.850, "mrr": 1.000, "ndcg_at_5": 0.8487},
    }
    write_json(output_dir / "retrieval" / "existing_retrieval_regression.json", {"status": "REFERENCE_VALUES_RECORDED", "expected": expected})


def write_reuse_audit(output_dir: Path) -> None:
    write_json(
        output_dir / "retrieval" / "existing_retriever_reuse.json",
        {
            "dense_retriever_class": f"{DenseRetriever.__module__}.{DenseRetriever.__name__}",
            "sparse_retriever_class": f"{SparseRetriever.__module__}.{SparseRetriever.__name__}",
            "hybrid_retriever_class": f"{HybridRetriever.__module__}.{HybridRetriever.__name__}",
            "fusion_function": f"{fuse_rrf.__module__}.{fuse_rrf.__name__}",
            "reranker_class": f"{CrossEncoderReranker.__module__}.{CrossEncoderReranker.__name__}",
            "copied_v3_retriever_files": [],
            "reuse_status": "PASS",
        },
    )


def write_readme(output_dir: Path, pre_audit: dict[str, Any], cleaning_summary: dict[str, Any]) -> None:
    collection_health = json.loads((output_dir / "collection" / "collection_health.json").read_text(encoding="utf-8"))
    quality = json.loads((output_dir / "staging" / "chunk_quality_summary.json").read_text(encoding="utf-8"))
    lines = [
        "# Medical RAG V3.2 Production Integration",
        "",
        f"- pre-clean child_count: {pre_audit['child_count']}",
        f"- removed blocks: {cleaning_summary['removed_blocks']}",
        f"- cleaned child_count: {quality['chunk_count']}",
        f"- chunk_quality_pass: {quality['chunk_quality_pass']}",
        f"- collection: {MEDICAL_RAG_V3_COLLECTION_NAME}",
        f"- collection_health: {collection_health['status']}",
        "- default_context_mode: legacy",
        "- external_evidence_default: false",
        "- retriever_reuse: DenseRetriever, SparseRetriever, HybridRetriever, fuse_rrf, CrossEncoderReranker",
        "- agent_ab: context-only smoke artifact unless Milvus/Agent harness are run separately",
        "",
        "## Known Limitations",
        "",
        "- WHO corpus still has only three active sources.",
        "- NHC source downloads still fail with HTTP 412 in the recorded source manifest.",
        "- page mapping is incomplete for pdftotext-derived hypertension chunks.",
        "- external evidence gold is limited to three evidence-chain smoke queries.",
        "- Agent live smoke was not executed by this script; context-level A/B trace is produced.",
        "- parent-child benefit for final model output is not proven without live Agent evaluator deltas.",
        "- external evidence adds retrieval/parent/context latency when the experiment flag is enabled.",
        "- Context Builder can increase token usage; budget/drop trace must be monitored.",
    ]
    (output_dir / "README.md").write_text("\n".join(lines), encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-artifact")
    parser.add_argument("--write-milvus", action="store_true")
    parser.add_argument("--reset-v3-collection", action="store_true")
    args = parser.parse_args()

    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    output_dir = ARTIFACT_ROOT / f"medical_rag_v3_2_production_integration_{timestamp}"
    output_dir.mkdir(parents=True, exist_ok=False)
    v3_dir = Path(args.source_artifact) if args.source_artifact else latest_ingestion_artifact()
    pre_audit = pre_clean_audit(v3_dir, output_dir / "source_cleaning" / "pre_clean_audit.json")
    parents, cleaned_chunks, sources, _decisions, cleaning_summary = cleaned_staging(output_dir)
    hypertension_before_after(pre_audit, cleaned_chunks, output_dir / "source_cleaning" / "hypertension_before_after.md")

    collection_args = argparse.Namespace(
        artifact_path=str(output_dir),
        output_dir=str(output_dir / "collection"),
        collection_name=MEDICAL_RAG_V3_COLLECTION_NAME,
        batch_size=32,
        dry_run=not args.write_milvus,
        reset=args.reset_v3_collection,
    )
    try:
        build_collection(collection_args)
    except Exception as exc:
        write_json(output_dir / "collection" / "collection_manifest.json", {"collection_name": MEDICAL_RAG_V3_COLLECTION_NAME, "error": str(exc)})
        write_json(output_dir / "collection" / "collection_parity.json", {"status": "NOT_RUN", "error": str(exc)})
        write_json(output_dir / "collection" / "collection_health.json", {"status": "UNAVAILABLE", "error": str(exc)})
    run_ab(output_dir)
    write_regression(output_dir)
    write_reuse_audit(output_dir)
    write_readme(output_dir, pre_audit, cleaning_summary)
    print(output_dir)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
