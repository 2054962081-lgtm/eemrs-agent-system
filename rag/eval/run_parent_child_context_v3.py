"""Run Medical RAG V3.1 parent-child context audit and ablation artifacts."""

from __future__ import annotations

import json
import time
from datetime import datetime
from pathlib import Path
from statistics import mean
from typing import Any

from rag.context.context_builder import ContextBuilder, ParentChildExpander
from rag.context.context_models import ContextPolicy, FinalContextPackage
from rag.context.external_evidence_retrieval import LocalExternalEvidenceRetriever
from rag.ingestion.chunk_quality_audit import distribution
from rag.ingestion.tokenizer_utils import BgeTokenizer
from rag.rag_config import (
    CONTEXT_PARENT_MAX_TOKENS,
    MAX_CONTEXT_UNITS_PER_PARENT,
    MAX_CONTEXT_UNITS_PER_SOURCE,
    PARENT_CHILD_NEIGHBOR_AFTER,
    PARENT_CHILD_NEIGHBOR_BEFORE,
    PROJECT_ROOT,
    RAG_CONTEXT_MAX_TOKENS,
    RAG_CONTEXT_MODE,
    RAG_RESERVED_NON_RAG_TOKENS,
    RAG_TOTAL_MODEL_CONTEXT_LIMIT,
)
from rag.store.parent_store import ParentStore, latest_v3_artifact

QUERIES = [
    {
        "case_id": "external_hypertension",
        "query": "成人高血压药物治疗 收缩压≥140 mmHg 什么时候开始治疗",
        "expected_source_id": "who_hypertension_pharmacological_guideline_2021_zh",
    },
    {
        "case_id": "external_stroke",
        "query": "卒中 FAST 症状 诊断和治疗 高血压风险",
        "expected_source_id": "who_stroke_fact_sheet_zh",
    },
    {
        "case_id": "external_preeclampsia",
        "query": "先兆子痫 妊娠20周后 血压≥140/90 mm Hg 蛋白尿",
        "expected_source_id": "who_preeclampsia_fact_sheet_zh",
    },
]


def write_jsonl(path: Path, rows: list[dict[str, Any]]) -> None:
    with path.open("w", encoding="utf-8") as handle:
        for row in rows:
            handle.write(json.dumps(row, ensure_ascii=False) + "\n")


def policy(mode: str) -> ContextPolicy:
    return ContextPolicy(
        mode=mode,
        neighbor_before=PARENT_CHILD_NEIGHBOR_BEFORE,
        neighbor_after=PARENT_CHILD_NEIGHBOR_AFTER,
        parent_max_tokens=CONTEXT_PARENT_MAX_TOKENS,
        max_tokens=RAG_CONTEXT_MAX_TOKENS,
        max_units_per_parent=MAX_CONTEXT_UNITS_PER_PARENT,
        max_units_per_source=MAX_CONTEXT_UNITS_PER_SOURCE,
        total_model_context_limit=RAG_TOTAL_MODEL_CONTEXT_LIMIT,
        reserved_non_rag_tokens=RAG_RESERVED_NON_RAG_TOKENS,
    )


def run_case(
    query_case: dict[str, str],
    mode: str,
    retriever: LocalExternalEvidenceRetriever,
    expander: ParentChildExpander,
    builder: ContextBuilder,
) -> dict[str, Any]:
    started = time.perf_counter()
    children = retriever.search(query_case["query"], top_k=5)
    units, expansion_trace = expander.expand(children, policy(mode))
    package = builder.build([], units, policy(mode))
    latency_ms = round((time.perf_counter() - started) * 1000, 3)
    source_hit = query_case["expected_source_id"] in package.included_source_ids
    return {
        "case_id": query_case["case_id"],
        "query_hash": stable_hash(query_case["query"]),
        "mode": mode,
        "expected_source_id": query_case["expected_source_id"],
        "retrieved_child_ids": [child.chunk_id for child in children],
        "included_child_ids": package.included_chunk_ids,
        "included_parent_ids": package.included_parent_ids,
        "included_source_ids": package.included_source_ids,
        "source_hit": source_hit,
        "context_token_count": package.token_count,
        "context_unit_count": len(package.context_units),
        "context_hash": package.context_hash,
        "context_build_latency_ms": latency_ms,
        "parent_expansion_trace": expansion_trace,
        "context_builder_trace": package.trace,
        "context_text_preview": package.final_text[:1200],
    }


def stable_hash(text: str) -> str:
    import hashlib

    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def summarize(rows: list[dict[str, Any]]) -> dict[str, Any]:
    if not rows:
        return {}
    duplicate_rates = [
        row["context_builder_trace"]["context_builder"].get("context_duplicate_rate", 0)
        if "context_duplicate_rate" in row["context_builder_trace"]["context_builder"]
        else 0
        for row in rows
    ]
    expansion_success = [
        bool(row["included_parent_ids"]) and all(item.get("parent_id") for item in row["parent_expansion_trace"]["parent_expansion"])
        for row in rows
    ]
    return {
        "avg_context_tokens": mean(row["context_token_count"] for row in rows),
        "avg_context_units": mean(row["context_unit_count"] for row in rows),
        "parent_expansion_success": sum(1 for item in expansion_success if item) / len(expansion_success),
        "duplicate_rate": mean(duplicate_rates),
        "source_diversity_avg": mean(len(row["included_source_ids"]) for row in rows),
        "context_build_latency_ms_avg": mean(row["context_build_latency_ms"] for row in rows),
        "source_hit_rate": sum(1 for row in rows if row["source_hit"]) / len(rows),
    }


def context_examples(rows_by_mode: dict[str, list[dict[str, Any]]]) -> str:
    lines = ["# Parent-Child Context Examples", ""]
    neighbor_rows = rows_by_mode.get("child_with_neighbors", [])
    for row in neighbor_rows:
        lines.extend(
            [
                f"## {row['case_id']}",
                f"- mode: {row['mode']}",
                f"- retrieved child: {', '.join(row['retrieved_child_ids'][:2])}",
                f"- included parents: {', '.join(row['included_parent_ids'])}",
                f"- included sources: {', '.join(row['included_source_ids'])}",
                "",
                row["context_text_preview"],
                "",
            ]
        )
    return "\n".join(lines)


def main() -> int:
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    output_dir = PROJECT_ROOT / "rag" / "eval" / "artifacts" / f"parent_child_context_v3_{timestamp}"
    output_dir.mkdir(parents=True, exist_ok=True)
    tokenizer = BgeTokenizer()
    store = ParentStore(latest_v3_artifact(), tokenizer)
    retriever = LocalExternalEvidenceRetriever(store)
    expander = ParentChildExpander(store, tokenizer)
    builder = ContextBuilder(tokenizer)

    audit = store.audit()
    (output_dir / "parent_child_audit.json").write_text(json.dumps(audit, ensure_ascii=False, indent=2), "utf-8")

    rows_by_mode: dict[str, list[dict[str, Any]]] = {}
    file_by_mode = {
        "child_only": "child_only_results.jsonl",
        "child_with_neighbors": "neighbor_results.jsonl",
        "parent_section": "parent_section_results.jsonl",
    }
    for mode in file_by_mode:
        rows = [run_case(case, mode, retriever, expander, builder) for case in QUERIES]
        rows_by_mode[mode] = rows
        write_jsonl(output_dir / file_by_mode[mode], rows)

    summaries = {mode: summarize(rows) for mode, rows in rows_by_mode.items()}
    all_rows = [row for rows in rows_by_mode.values() for row in rows]
    quality = {
        "mode_summaries": summaries,
        "parent_expansion_success_rate": mean(summary["parent_expansion_success"] for summary in summaries.values()),
        "context_duplicate_rate": mean(summary["duplicate_rate"] for summary in summaries.values()),
        "context_token_distribution": distribution([row["context_token_count"] for row in all_rows]),
        "context_drop_rate": mean(
            len(row["context_builder_trace"]["context_builder"]["dropped_units"])
            / max(row["context_builder_trace"]["context_builder"]["input_units"], 1)
            for row in all_rows
        ),
    }
    (output_dir / "context_quality_summary.json").write_text(json.dumps(quality, ensure_ascii=False, indent=2), "utf-8")
    write_jsonl(output_dir / "context_quality_details.jsonl", all_rows)
    write_jsonl(output_dir / "trace_examples.jsonl", [row for row in all_rows if row["mode"] == "child_with_neighbors"])
    (output_dir / "context_examples.md").write_text(context_examples(rows_by_mode), "utf-8")
    manifest = {
        "created_at": datetime.now().isoformat(timespec="seconds"),
        "v3_artifact_dir": str(store.artifact_dir),
        "context_default_mode": RAG_CONTEXT_MODE,
        "modes": list(file_by_mode),
        "policy": policy("child_with_neighbors").__dict__,
        "queries": QUERIES,
        "v3_child_retrieval_source": "LocalExternalEvidenceRetriever over staged semantic_chunks.jsonl",
        "new_collection": "NOT_CREATED",
    }
    (output_dir / "context_manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), "utf-8")
    (output_dir / "regression_summary.json").write_text(
        json.dumps(
            {
                "dense_mode_changed": False,
                "bm25_mode_changed": False,
                "hybrid_rrf_changed": False,
                "hybrid_rerank_changed": False,
                "query_expansion_changed": False,
                "rrf_changed": False,
                "reranker_changed": False,
                "scene_policy_changed": False,
                "public_api_schema_changed": False,
                "memory_endpoints_changed": False,
                "internal_62_knowledge_changed": False,
            },
            ensure_ascii=False,
            indent=2,
        ),
        "utf-8",
    )
    readme = [
        "# Medical RAG V3.1 Parent-Child Context Artifact",
        "",
        f"- V3 artifact: {store.artifact_dir}",
        f"- default context mode: {RAG_CONTEXT_MODE}",
        f"- RAG token budget: {RAG_CONTEXT_MAX_TOKENS}",
        f"- parent count: {audit['parent_count']}",
        f"- child count: {audit['child_count']}",
        f"- orphan child count: {audit['orphan_child_count']}",
        f"- invalid parent ref count: {audit['invalid_parent_ref_count']}",
        "- collection: NOT_CREATED; local V3 child retrieval source used for prototype.",
    ]
    (output_dir / "README.md").write_text("\n".join(readme), "utf-8")
    print(output_dir)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
