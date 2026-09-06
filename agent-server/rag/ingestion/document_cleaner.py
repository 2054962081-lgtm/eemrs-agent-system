"""Deterministic document cleaning for Medical RAG external evidence."""

from __future__ import annotations

import json
import re
from collections import Counter
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any

from rag.schema.medical_ingestion import DocumentBlock, ParsedDocument


REMOVAL_REASONS = {
    "COPYRIGHT",
    "TABLE_OF_CONTENTS",
    "NAVIGATION",
    "HEADER_FOOTER",
    "PUBLICATION_FRONT_MATTER",
    "LOW_INFORMATION",
}

CLINICAL_PROTECTION_PATTERNS = [
    re.compile(pattern, re.I)
    for pattern in [
        r"WHO recommends",
        r"世卫组织建议",
        r"推荐",
        r"建议",
        r"诊断",
        r"治疗",
        r"症状",
        r"风险",
        r"预防",
        r"并发症",
        r"血压",
        r"卒中",
        r"先兆子痫",
        r"高血压",
        r"面瘫",
        r"吞咽困难",
        r"癫痫",
        r"脑水肿",
        r"蛋白尿",
        r"饮酒",
        r"\d+\s*(mm\s*Hg|mg|g|小时|周|%)",
        r"≥|≤|>",
    ]
]


@dataclass(frozen=True)
class CleaningDecision:
    block_id: str
    source_id: str
    reason: str | None
    rule: str | None
    text_preview: str
    removed: bool

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


def normalize_text(text: str) -> str:
    return re.sub(r"\s+", " ", text or "").strip()


def compact_text(text: str) -> str:
    return re.sub(r"\s+", "", text or "").lower()


def is_clinical_text(text: str) -> bool:
    return any(pattern.search(text or "") for pattern in CLINICAL_PROTECTION_PATTERNS)


def looks_like_toc(text: str) -> bool:
    value = normalize_text(text)
    if value in {"目录", "Contents", "Table of contents"} or value.startswith("目录 "):
        return True
    dotted = re.search(r"(\.{3,}|…{2,})\s*\d{1,3}", value)
    numbered_lines = len(re.findall(r"(第[一二三四五六七八九十0-9]+章|^\d+(\.\d+){0,3})", value, re.M))
    page_numbers = len(re.findall(r"\s\d{1,3}(\s|$)", value))
    return bool(dotted) or (numbered_lines >= 3 and page_numbers >= 3)


def looks_like_copyright(text: str) -> bool:
    value = normalize_text(text)
    strong_patterns = [
        r"©",
        r"All rights reserved",
        r"CC BY",
        r"知识共享",
        r"许可协议",
        r"ISBN",
        r"在版编目",
        r"销售、版权和许可",
        r"第三方材料",
        r"免责声明",
        r"凡提及某些公司",
        r"专利产品名称",
        r"不意味着.*认可或推荐",
        r"采取一切合理的预防措施",
        r"材料的分发无.*保证",
        r"因使用这些材料造成的损失",
    ]
    if any(re.search(pattern, value, re.I) for pattern in strong_patterns):
        return True
    weak_patterns = [
        r"Copyright",
    ]
    return any(re.search(pattern, value, re.I) for pattern in weak_patterns) and not is_clinical_text(value)


def looks_like_front_matter(text: str, order: int, total_blocks: int) -> bool:
    if total_blocks <= 0 or order > max(12, total_blocks * 0.18):
        return False
    value = normalize_text(text)
    patterns = [
        r"建议的引用格式",
        r"出版",
        r"鸣谢",
        r"设计ations employed|designations employed",
        r"World Health Organization;?\s*\d{4}",
        r"日内瓦：世界卫生组织",
    ]
    return any(re.search(pattern, value, re.I) for pattern in patterns) and not is_clinical_text(value)


def looks_like_navigation(text: str) -> bool:
    value = normalize_text(text)
    nav_items = {
        "Home",
        "Newsroom",
        "Fact sheets",
        "Health topics",
        "Menu",
        "Share",
        "Download",
        "Previous",
        "Next",
        "Subscribe",
        "Donate",
        "主页",
        "新闻室",
        "实况报道",
        "卫生主题",
        "菜单",
        "分享",
        "下载",
        "上一个",
        "下一个",
    }
    if value in nav_items:
        return True
    parts = [part.strip() for part in re.split(r"[|/·•\n]+", value) if part.strip()]
    return len(parts) >= 4 and sum(1 for part in parts if part in nav_items) >= 3


def looks_like_low_information(text: str) -> bool:
    value = normalize_text(text)
    if is_clinical_text(value):
        return False
    alnum = re.findall(r"[\u4e00-\u9fffA-Za-z0-9]", value)
    unique = set(alnum)
    if len(value) <= 2:
        return True
    if re.search(r"[\u4e00-\u9fff]", value) and len(value) >= 3:
        return False
    return len(alnum) <= 8 and len(unique) <= 4


def repeated_header_footer_text(blocks: list[DocumentBlock]) -> set[str]:
    by_text: Counter[str] = Counter()
    pages_by_text: dict[str, set[int]] = {}
    for block in blocks:
        value = compact_text(block.text)
        if not value or len(value) > 80:
            continue
        by_text[value] += 1
        if block.page is not None:
            pages_by_text.setdefault(value, set()).add(block.page)
    repeated: set[str] = set()
    for value, count in by_text.items():
        page_count = len(pages_by_text.get(value, set()))
        if count >= 3 or page_count >= 3:
            repeated.add(value)
    return repeated


class DocumentCleaner:
    """Rule-based cleaner that removes non-medical boilerplate before chunking."""

    def clean(self, document: ParsedDocument) -> tuple[ParsedDocument, list[CleaningDecision], dict[str, Any]]:
        repeated = repeated_header_footer_text(document.blocks)
        total = len(document.blocks)
        kept: list[DocumentBlock] = []
        decisions: list[CleaningDecision] = []
        removed_by_reason: Counter[str] = Counter()
        for block in document.blocks:
            reason, rule = self.classify(block, total, repeated)
            removed = reason is not None
            if removed:
                removed_by_reason[reason] += 1
            else:
                kept.append(block)
            decisions.append(
                CleaningDecision(
                    block_id=block.block_id,
                    source_id=document.source.source_id,
                    reason=reason,
                    rule=rule,
                    text_preview=normalize_text(block.text)[:240],
                    removed=removed,
                )
            )
        cleaned = ParsedDocument(
            source=document.source,
            pages=document.pages,
            blocks=kept,
            parser_name=document.parser_name,
            parser_status=document.parser_status,
            warnings=[*document.warnings, f"document_cleaner_removed:{sum(removed_by_reason.values())}"],
        )
        summary = {
            "source_id": document.source.source_id,
            "before_blocks": total,
            "after_blocks": len(kept),
            "removed_blocks": sum(removed_by_reason.values()),
            "removed_ratio": (sum(removed_by_reason.values()) / total) if total else 0,
            "removed_by_reason": dict(removed_by_reason),
            "cleaner_status": "OK",
        }
        return cleaned, decisions, summary

    def classify(self, block: DocumentBlock, total_blocks: int, repeated: set[str]) -> tuple[str | None, str | None]:
        text = normalize_text(block.text)
        if not text:
            return "LOW_INFORMATION", "empty_after_normalization"
        if compact_text(text) in repeated and not is_clinical_text(text):
            return "HEADER_FOOTER", "repeated_short_block"
        if looks_like_navigation(text):
            return "NAVIGATION", "known_navigation_pattern"
        if looks_like_toc(text):
            return "TABLE_OF_CONTENTS", "toc_structure_or_heading"
        if looks_like_copyright(text):
            return "COPYRIGHT", "copyright_license_publication_pattern"
        if looks_like_front_matter(text, block.order, total_blocks):
            return "PUBLICATION_FRONT_MATTER", "early_publication_front_matter_pattern"
        if looks_like_low_information(text):
            return "LOW_INFORMATION", "short_low_information_nonclinical"
        return None, None


def write_cleaning_report(output_dir: Path, decisions: list[CleaningDecision], summaries: list[dict[str, Any]]) -> dict[str, Any]:
    output_dir.mkdir(parents=True, exist_ok=True)
    with (output_dir / "cleaning_report.jsonl").open("w", encoding="utf-8") as handle:
        for decision in decisions:
            if decision.removed:
                handle.write(json.dumps(decision.to_dict(), ensure_ascii=False) + "\n")
    before = sum(item["before_blocks"] for item in summaries)
    after = sum(item["after_blocks"] for item in summaries)
    removed_by_reason: Counter[str] = Counter()
    for item in summaries:
        removed_by_reason.update(item.get("removed_by_reason") or {})
    summary = {
        "before_blocks": before,
        "after_blocks": after,
        "removed_blocks": before - after,
        "removed_ratio": ((before - after) / before) if before else 0,
        "removed_by_reason": dict(removed_by_reason),
        "cleaner_status": "OK",
        "source_summaries": summaries,
    }
    (output_dir / "cleaning_summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    return summary
