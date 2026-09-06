"""Protected medical span detection for safe chunk boundaries."""

from __future__ import annotations

import re
from dataclasses import dataclass

from rag.schema.medical_ingestion import ProtectedSpan

NAMED_TERMS = (
    "急性冠脉综合征",
    "急性缺血性脑卒中",
    "短暂性脑缺血发作",
    "先兆子痫",
    "子痫",
    "HELLP综合征",
    "妊娠期高血压",
    "糖化血红蛋白",
    "天冬氨酸氨基转移酶",
    "丙氨酸氨基转移酶",
)

ABBREVIATION_RE = re.compile(r"[A-Za-z][A-Za-z0-9-]{1,12}(?:综合征)?")
NUMERIC_UNIT_RE = re.compile(
    r"(?:[<>≤≥=低于高于不少于不超过约达至]*\s*)?"
    r"\d+(?:\.\d+)?(?:/\d+(?:\.\d+)?)?"
    r"(?:\s*[~\-–至]\s*\d+(?:\.\d+)?)?"
    r"\s*(?:×\s*10\^?\d+\s*/\s*L|mmHg|mg/dL|g/24\s*h|μmol/L|umol/L|mmol/L|mg|g|μg|ug|mL|ml|L|周|小时|天|分钟|%|次/分)"
)
COMPARISON_UNIT_RE = re.compile(
    r"(?:收缩压|舒张压|血压|血小板|蛋白尿|肌酐|ALT|AST|HbA1c)?"
    r"\s*(?:≥|≤|>|<|低于|高于|不少于|不超过)\s*"
    r"\d+(?:\.\d+)?(?:/\d+(?:\.\d+)?)?\s*(?:×\s*10\^?\d+\s*/\s*L|mmHg|mg/dL|g/24\s*h|μmol/L|umol/L|mmol/L|%)"
)
CN_LONG_TERM_RE = re.compile(r"[\u4e00-\u9fff]{4,18}(?:病|症|征|炎|癌|综合征|缺血|出血|治疗|诊断|指南)")
GLOSSARY_RE = re.compile(r"([\u4e00-\u9fff]{2,24})[（(]([A-Za-z][A-Za-z0-9-]{1,12})[）)]")


@dataclass
class MedicalTermProtector:
    extra_terms: tuple[str, ...] = ()

    def detect(self, text: str) -> list[ProtectedSpan]:
        spans: list[ProtectedSpan] = []
        terms = set(NAMED_TERMS) | set(self.extra_terms)
        for full, abbr in GLOSSARY_RE.findall(text or ""):
            terms.add(full)
            terms.add(abbr)
            terms.add(f"{full}（{abbr}）")
            terms.add(f"{full}({abbr})")
        for term in sorted(terms, key=len, reverse=True):
            for match in re.finditer(re.escape(term), text or ""):
                spans.append(ProtectedSpan(match.start(), match.end(), match.group(0), "medical_term"))
        for regex, kind in (
            (COMPARISON_UNIT_RE, "numeric_unit"),
            (NUMERIC_UNIT_RE, "numeric_unit"),
            (ABBREVIATION_RE, "abbreviation"),
            (CN_LONG_TERM_RE, "medical_term_candidate"),
        ):
            for match in regex.finditer(text or ""):
                spans.append(ProtectedSpan(match.start(), match.end(), match.group(0), kind))
        return merge_spans(spans)


def merge_spans(spans: list[ProtectedSpan]) -> list[ProtectedSpan]:
    ordered = sorted(spans, key=lambda span: (span.start, -(span.end - span.start)))
    merged: list[ProtectedSpan] = []
    for span in ordered:
        if not merged or span.start >= merged[-1].end:
            merged.append(span)
            continue
        prev = merged[-1]
        if span.end > prev.end:
            merged[-1] = ProtectedSpan(prev.start, span.end, prev.text + span.text, prev.kind)
    return merged


def is_inside_protected_span(boundary: int, spans: list[ProtectedSpan]) -> ProtectedSpan | None:
    for span in spans:
        if span.start < boundary < span.end:
            return span
    return None
