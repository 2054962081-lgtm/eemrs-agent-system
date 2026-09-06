"""Deterministic context expansion and budget selection for Medical RAG V3.1."""

from __future__ import annotations

import hashlib
import re
import time
from collections import Counter, defaultdict
from typing import Iterable

from rag.context.context_models import ContextPolicy, ContextUnit, FinalContextPackage
from rag.ingestion.tokenizer_utils import BgeTokenizer
from rag.store.parent_store import ParentStore, StoredChild

EXTERNAL = "external_evidence"
INTERNAL = "internal_protocol"


def stable_hash(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def normalize_text(text: str) -> str:
    return re.sub(r"\s+", "", text or "").lower()


def source_label(unit: ContextUnit) -> str:
    if unit.knowledge_origin == EXTERNAL:
        date = unit.source_title
        if unit.publisher or unit.source_title:
            date = " | ".join(part for part in [unit.publisher, unit.source_title] if part)
        return f"[{date}]"
    return f"[内部问诊规则 | {unit.doc_type}]"


def format_unit(unit: ContextUnit) -> str:
    heading = " > ".join(unit.heading_path)
    lines = [source_label(unit)]
    if heading:
        lines.append(f"章节：{heading}")
    lines.append(unit.text.strip())
    return "\n".join(line for line in lines if line)


class ParentChildExpander:
    def __init__(self, store: ParentStore, tokenizer: BgeTokenizer | None = None):
        self.store = store
        self.tokenizer = tokenizer or store.tokenizer

    def expand(self, selected_children: Iterable[StoredChild], policy: ContextPolicy) -> tuple[list[ContextUnit], dict]:
        started = time.perf_counter()
        selected = list(selected_children)
        by_parent: dict[str, list[StoredChild]] = defaultdict(list)
        for child in selected:
            by_parent[child.parent_id].append(child)
        units: list[ContextUnit] = []
        traces = []
        for parent_id in sorted(by_parent, key=lambda pid: min(child.retrieval_rank or 9999 for child in by_parent[pid])):
            matched = sorted(by_parent[parent_id], key=lambda child: (child.retrieval_rank or 9999, child.child_order))
            best = matched[0]
            unit = self._expand_parent(parent_id, matched, best, policy)
            units.append(unit)
            traces.append(
                {
                    "chunk_id": best.chunk_id,
                    "parent_id": parent_id,
                    "selected": True,
                    "matched_child_ids": [child.chunk_id for child in matched],
                    "parent_expansion": {
                        "mode": unit.expansion_mode,
                        "expanded_child_ids": unit.expanded_child_ids,
                        "expanded_token_count": unit.token_count,
                        "expansion_fallback_reason": unit.expansion_fallback_reason,
                    },
                }
            )
        trace = {
            "input_child_count": len(selected),
            "matched_parent_count": len(by_parent),
            "expanded_unit_count": len(units),
            "parent_lookup_ms": round((time.perf_counter() - started) * 1000, 3),
            "parent_expansion": traces,
        }
        return units, trace

    def _expand_parent(
        self,
        parent_id: str,
        matched: list[StoredChild],
        best: StoredChild,
        policy: ContextPolicy,
    ) -> ContextUnit:
        parent = self.store.get_parent(parent_id)
        fallback = None
        if policy.mode == "child_only":
            children = [best]
            mode = "child_only"
            text = best.chunk_text
        elif policy.mode == "parent_section" and parent and parent.token_count <= policy.parent_max_tokens:
            children = self.store.get_children(parent_id)
            mode = "parent_section"
            text = parent.text
        else:
            if policy.mode == "parent_section":
                fallback = "PARENT_EXCEEDS_TOKEN_LIMIT"
            mode = "child_with_neighbors" if policy.mode in {"child_with_neighbors", "child_neighbors", "parent_section"} else "child_only"
            children = self.store.get_sibling_children(parent_id, best.chunk_id, policy.neighbor_before, policy.neighbor_after)
            if not children:
                children = [best]
                mode = "child_only"
            text = "\n".join(child.chunk_text for child in children)
        token_count = self.tokenizer.count(text)
        unit = ContextUnit(
            context_unit_id=f"ctx_{parent_id}_{stable_hash('|'.join(child.chunk_id for child in matched))[:12]}",
            source_id=best.source_id,
            parent_id=parent_id,
            matched_child_ids=[child.chunk_id for child in matched],
            knowledge_origin=best.knowledge_origin,
            doc_type=best.document_type,
            heading_path=best.heading_path,
            text=text,
            token_count=token_count,
            retrieval_rank=min(child.retrieval_rank or 9999 for child in matched),
            best_rerank_score=max((child.score for child in matched if child.score is not None), default=None),
            expansion_mode=mode,
            source_title=best.source_title,
            publisher=best.publisher,
            page_start=min((child.page_start for child in children if child.page_start is not None), default=None),
            page_end=max((child.page_end for child in children if child.page_end is not None), default=None),
            expanded_child_ids=[child.chunk_id for child in children],
            source_url=best.source_url,
            expansion_fallback_reason=fallback,
        )
        fallbacks: list[ContextUnit] = []
        if mode == "parent_section":
            neighbor_policy = ContextPolicy(
                mode="child_with_neighbors",
                neighbor_before=policy.neighbor_before,
                neighbor_after=policy.neighbor_after,
                parent_max_tokens=policy.parent_max_tokens,
                max_tokens=policy.max_tokens,
                max_units_per_parent=policy.max_units_per_parent,
                max_units_per_source=policy.max_units_per_source,
                total_model_context_limit=policy.total_model_context_limit,
                reserved_non_rag_tokens=policy.reserved_non_rag_tokens,
            )
            fallbacks.append(self._expand_parent(parent_id, matched, best, neighbor_policy))
        if mode != "child_only":
            child_policy = ContextPolicy(
                mode="child_only",
                neighbor_before=policy.neighbor_before,
                neighbor_after=policy.neighbor_after,
                parent_max_tokens=policy.parent_max_tokens,
                max_tokens=policy.max_tokens,
                max_units_per_parent=policy.max_units_per_parent,
                max_units_per_source=policy.max_units_per_source,
                total_model_context_limit=policy.total_model_context_limit,
                reserved_non_rag_tokens=policy.reserved_non_rag_tokens,
            )
            fallbacks.append(self._expand_parent(parent_id, matched, best, child_policy))
        unit.fallback_units = fallbacks
        return unit


class ContextBuilder:
    def __init__(self, tokenizer: BgeTokenizer | None = None):
        self.tokenizer = tokenizer or BgeTokenizer()

    def build(
        self,
        internal_context_units: list[ContextUnit],
        external_context_units: list[ContextUnit],
        policy: ContextPolicy,
    ) -> FinalContextPackage:
        started = time.perf_counter()
        ordered = self._ordered_units(internal_context_units, external_context_units)
        dedup_started = time.perf_counter()
        deduped, dedup_drops = self._dedup(ordered, policy)
        dedup_ms = round((time.perf_counter() - dedup_started) * 1000, 3)
        included: list[ContextUnit] = []
        dropped = list(dedup_drops)
        token_total = 0
        for unit in deduped:
            unit_to_add = self._fit_unit(unit, policy)
            if unit_to_add is None:
                dropped.append({"id": unit.context_unit_id, "reason": "TOKEN_BUDGET"})
                continue
            formatted = format_unit(unit_to_add)
            unit_tokens = self.tokenizer.count(formatted)
            if token_total + unit_tokens > policy.max_tokens:
                dropped.append({"id": unit.context_unit_id, "reason": "TOKEN_BUDGET"})
                continue
            included.append(unit_to_add)
            token_total += unit_tokens
        final_text = "\n\n".join(format_unit(unit) for unit in included)
        context_hash = stable_hash(final_text)
        included_chunk_ids = []
        for unit in included:
            included_chunk_ids.extend(unit.expanded_child_ids or unit.matched_child_ids)
        included_parent_ids = [unit.parent_id for unit in included if unit.parent_id]
        included_source_ids = sorted({unit.source_id for unit in included})
        duplicate_rate = context_duplicate_rate(included)
        trace = {
            "context_builder": {
                "input_units": len(ordered),
                "deduplicated_units": len(deduped),
                "included_units": len(included),
                "dropped_units": dropped,
                "token_budget": policy.max_tokens,
                "final_token_count": token_total,
                "included_parent_ids": included_parent_ids,
                "context_hash": context_hash,
                "context_duplicate_rate": duplicate_rate,
                "context_token_utilization": (token_total / policy.max_tokens) if policy.max_tokens else 0,
                "dedup_ms": dedup_ms,
                "context_build_ms": round((time.perf_counter() - started) * 1000, 3),
            }
        }
        return FinalContextPackage(
            context_units=included,
            final_text=final_text,
            token_count=token_total,
            budget=policy.max_tokens,
            included_chunk_ids=list(dict.fromkeys(included_chunk_ids)),
            included_parent_ids=list(dict.fromkeys(included_parent_ids)),
            included_source_ids=included_source_ids,
            dropped_units=dropped,
            context_transform={
                "mode": policy.mode,
                "total_model_context_limit": policy.total_model_context_limit,
                "reserved_non_rag_tokens": policy.reserved_non_rag_tokens,
                "rag_context_budget": policy.max_tokens,
                "context_duplicate_rate": duplicate_rate,
                "context_token_utilization": (token_total / policy.max_tokens) if policy.max_tokens else 0,
            },
            context_hash=context_hash,
            trace=trace,
        )

    def _ordered_units(self, internal_units: list[ContextUnit], external_units: list[ContextUnit]) -> list[ContextUnit]:
        return sorted(
            [*internal_units, *external_units],
            key=lambda unit: (0 if unit.knowledge_origin == INTERNAL else 1, unit.retrieval_rank, unit.context_unit_id),
        )

    def _dedup(self, units: list[ContextUnit], policy: ContextPolicy) -> tuple[list[ContextUnit], list[dict[str, str]]]:
        seen_chunks: set[str] = set()
        seen_parent_expansion: set[str] = set()
        seen_text_hashes: set[str] = set()
        parent_counts: Counter[str] = Counter()
        source_counts: Counter[str] = Counter()
        kept: list[ContextUnit] = []
        dropped: list[dict[str, str]] = []
        for unit in units:
            duplicate_chunk = any(chunk_id in seen_chunks for chunk_id in unit.expanded_child_ids or unit.matched_child_ids)
            if duplicate_chunk:
                dropped.append({"id": unit.context_unit_id, "reason": "DUPLICATE_CHUNK"})
                continue
            expansion_key = f"{unit.parent_id}:{','.join(unit.expanded_child_ids)}"
            if expansion_key in seen_parent_expansion:
                dropped.append({"id": unit.context_unit_id, "reason": "DUPLICATE_PARENT_EXPANSION"})
                continue
            text_hash = stable_hash(normalize_text(unit.text))
            if text_hash in seen_text_hashes:
                dropped.append({"id": unit.context_unit_id, "reason": "DUPLICATE_TEXT"})
                continue
            if unit.parent_id and parent_counts[unit.parent_id] >= policy.max_units_per_parent:
                dropped.append({"id": unit.context_unit_id, "reason": "MAX_CONTEXT_UNITS_PER_PARENT"})
                continue
            if source_counts[unit.source_id] >= policy.max_units_per_source:
                dropped.append({"id": unit.context_unit_id, "reason": "MAX_CONTEXT_UNITS_PER_SOURCE"})
                continue
            kept.append(unit)
            for chunk_id in unit.expanded_child_ids or unit.matched_child_ids:
                seen_chunks.add(chunk_id)
            seen_parent_expansion.add(expansion_key)
            seen_text_hashes.add(text_hash)
            if unit.parent_id:
                parent_counts[unit.parent_id] += 1
            source_counts[unit.source_id] += 1
        return kept, dropped

    def _fit_unit(self, unit: ContextUnit, policy: ContextPolicy) -> ContextUnit | None:
        if self.tokenizer.count(format_unit(unit)) <= policy.max_tokens:
            return unit
        for fallback in unit.fallback_units:
            fitted = self._fit_unit(fallback, policy)
            if fitted is not None:
                fitted.expansion_fallback_reason = fitted.expansion_fallback_reason or "TOKEN_BUDGET_DOWNGRADE"
                return fitted
        return None


def context_duplicate_rate(units: list[ContextUnit]) -> float:
    if not units:
        return 0.0
    hashes = [stable_hash(normalize_text(unit.text)) for unit in units]
    duplicates = len(hashes) - len(set(hashes))
    return duplicates / len(hashes)
