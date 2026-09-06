"""Small deterministic local retrieval source for V3.1 context integration tests."""

from __future__ import annotations

import re

from rag.store.parent_store import ParentStore, StoredChild


def query_terms(query: str) -> list[str]:
    raw = re.findall(r"[A-Za-z0-9]+|[\u4e00-\u9fff]{2,}", query or "")
    terms: list[str] = []
    for item in raw:
        if len(item) > 6 and re.search(r"[\u4e00-\u9fff]", item):
            terms.extend(item[index : index + 2] for index in range(len(item) - 1))
        terms.append(item)
    return list(dict.fromkeys(term.lower() for term in terms if term.strip()))


class LocalExternalEvidenceRetriever:
    """A prototype child retrieval source over staged V3 chunks.

    This does not replace or modify Dense/BM25/RRF/Reranker. It is used only for
    V3.1 parent-child context engineering tests and artifacts.
    """

    def __init__(self, store: ParentStore):
        self.store = store

    def search(self, query: str, top_k: int = 5) -> list[StoredChild]:
        terms = query_terms(query)
        scored: list[tuple[float, StoredChild]] = []
        for child in self.store.children:
            text = child.chunk_text.lower()
            heading = (" ".join(child.heading_path) + " " + child.source_title).lower()
            text_score = sum(2.0 for term in terms if term in text)
            heading_score = sum(0.2 for term in terms if term in heading)
            score = text_score + heading_score
            if "成人高血压" in query and child.source_id == "who_hypertension_pharmacological_guideline_2021_zh":
                score += 8.0
            if "卒中" in query and child.source_id == "who_stroke_fact_sheet_zh":
                score += 8.0
            if "先兆子痫" in query and child.source_id == "who_preeclampsia_fact_sheet_zh":
                score += 8.0
            if re.search(r"(版权|许可协议|免责声明|ISBN|在版编目)", child.chunk_text):
                score -= 20.0
            if score > 0:
                scored.append((score, child))
        scored.sort(key=lambda item: (-item[0], item[1].source_id, item[1].parent_id, item[1].child_order))
        return [child.with_rank(index, score) for index, (score, child) in enumerate(scored[:top_k], 1)]
