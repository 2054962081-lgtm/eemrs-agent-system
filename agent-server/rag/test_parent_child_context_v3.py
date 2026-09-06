from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from rag.context.context_builder import ContextBuilder, ParentChildExpander
from rag.context.context_models import ContextPolicy, ContextUnit
from rag.context.external_evidence_retrieval import LocalExternalEvidenceRetriever
from rag.store.parent_store import ParentStore, latest_v3_artifact


class FakeTokenizer:
    model_max_length = 512

    def count(self, text: str, add_special_tokens: bool = True) -> int:
        return len((text or "").split()) + (2 if add_special_tokens else 0)


def write_jsonl(path: Path, rows: list[dict]) -> None:
    with path.open("w", encoding="utf-8") as handle:
        for row in rows:
            handle.write(json.dumps(row, ensure_ascii=False) + "\n")


def child(parent_id: str, order: int, text: str, source_id: str = "source_a") -> dict:
    chunk_id = f"{source_id}_c_aaaaaaaaaaaa_{order:04d}_{order:012d}"
    return {
        "chunk_id": chunk_id,
        "parent_id": parent_id,
        "source_id": source_id,
        "source_title": "测试来源",
        "publisher": "WHO",
        "publication_date": "2021",
        "version": "2021",
        "source_url": "https://www.who.int/zh/test",
        "document_type": "guideline",
        "source_status": "active",
        "license_status": "verified_open",
        "heading_path": ["测试来源", "章节"],
        "page_start": None,
        "page_end": None,
        "block_types": ["paragraph"],
        "chunk_text": text,
        "embedding_text": text,
        "char_count": len(text),
        "token_count": len(text.split()),
        "embedding_token_count": len(text.split()),
        "embedding_prefix_tokens": 0,
        "protected_terms": [],
        "abbreviations": [],
        "numeric_unit_spans": [],
        "split_reason": "normal",
        "semantic_boundary_type": "block",
        "knowledge_origin": "external_evidence",
        "source_span": {"block_ids": [f"b{order}"]},
    }


class ParentChildContextUnitTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = tempfile.TemporaryDirectory()
        artifact = Path(self.tmp.name)
        write_jsonl(
            artifact / "parents.jsonl",
            [
                {
                    "parent_id": "parent_a",
                    "source_id": "source_a",
                    "source_title": "测试来源",
                    "heading_path": ["测试来源", "章节"],
                    "page_start": None,
                    "page_end": None,
                    "text_hash": "x",
                }
            ],
        )
        write_jsonl(
            artifact / "semantic_chunks.jsonl",
            [
                child("parent_a", 1, "first child extra extra extra extra extra extra"),
                child("parent_a", 2, "middle child 收缩压 ≥140 mmHg"),
                child("parent_a", 3, "last child extra extra extra extra extra extra"),
            ],
        )
        self.store = ParentStore(artifact, FakeTokenizer())
        self.expander = ParentChildExpander(self.store, FakeTokenizer())
        self.builder = ContextBuilder(FakeTokenizer())

    def tearDown(self) -> None:
        self.tmp.cleanup()

    def ranked_child(self, order: int):
        return self.store.get_children("parent_a")[order - 1].with_rank(1, 1.0)

    def test_child_only(self) -> None:
        units, _ = self.expander.expand([self.ranked_child(2)], ContextPolicy(mode="child_only"))
        self.assertEqual(units[0].expanded_child_ids, [self.ranked_child(2).chunk_id])
        self.assertEqual(units[0].expansion_mode, "child_only")

    def test_middle_child_neighbors_order(self) -> None:
        units, _ = self.expander.expand([self.ranked_child(2)], ContextPolicy(mode="child_with_neighbors"))
        self.assertEqual(units[0].expanded_child_ids, [c.chunk_id for c in self.store.get_children("parent_a")])

    def test_first_child_has_no_invalid_before(self) -> None:
        units, _ = self.expander.expand([self.ranked_child(1)], ContextPolicy(mode="child_with_neighbors"))
        self.assertEqual(units[0].expanded_child_ids, [self.ranked_child(1).chunk_id, self.ranked_child(2).chunk_id])

    def test_last_child_has_no_invalid_after(self) -> None:
        units, _ = self.expander.expand([self.ranked_child(3)], ContextPolicy(mode="child_with_neighbors"))
        self.assertEqual(units[0].expanded_child_ids, [self.ranked_child(2).chunk_id, self.ranked_child(3).chunk_id])

    def test_same_parent_expands_once(self) -> None:
        units, _ = self.expander.expand([self.ranked_child(1), self.ranked_child(2), self.ranked_child(3)], ContextPolicy())
        self.assertEqual(len(units), 1)
        self.assertEqual(len(units[0].matched_child_ids), 3)

    def test_parent_section_when_under_limit(self) -> None:
        units, _ = self.expander.expand([self.ranked_child(2)], ContextPolicy(mode="parent_section", parent_max_tokens=200))
        self.assertEqual(units[0].expansion_mode, "parent_section")
        self.assertIn("first child", units[0].text)

    def test_parent_section_fallback_when_parent_too_long(self) -> None:
        units, _ = self.expander.expand([self.ranked_child(2)], ContextPolicy(mode="parent_section", parent_max_tokens=2))
        self.assertEqual(units[0].expansion_mode, "child_with_neighbors")
        self.assertEqual(units[0].expansion_fallback_reason, "PARENT_EXCEEDS_TOKEN_LIMIT")

    def test_budget_downgrades_to_child(self) -> None:
        units, _ = self.expander.expand([self.ranked_child(2)], ContextPolicy(mode="parent_section", parent_max_tokens=200))
        units[0].text = units[0].text + " extra extra extra extra extra extra extra extra"
        package = self.builder.build([], units, ContextPolicy(mode="parent_section", max_tokens=20))
        self.assertTrue(package.context_units)
        self.assertEqual(package.context_units[0].expansion_mode, "child_only")

    def test_budget_drops_without_truncating(self) -> None:
        unit = ContextUnit(
            context_unit_id="huge",
            source_id="source_a",
            parent_id="parent_a",
            matched_child_ids=["c1"],
            knowledge_origin="external_evidence",
            doc_type="guideline",
            heading_path=["测试"],
            text="too many tokens for tiny budget",
            token_count=6,
            retrieval_rank=1,
            best_rerank_score=None,
            expansion_mode="child_only",
            source_title="测试来源",
            publisher="WHO",
            page_start=None,
            page_end=None,
            expanded_child_ids=["c1"],
        )
        package = self.builder.build([], [unit], ContextPolicy(max_tokens=2))
        self.assertEqual(package.context_units, [])
        self.assertEqual(package.dropped_units[0]["reason"], "TOKEN_BUDGET")

    def test_duplicate_child_dedup(self) -> None:
        units, _ = self.expander.expand([self.ranked_child(2)], ContextPolicy(mode="child_only"))
        package = self.builder.build([], [units[0], units[0]], ContextPolicy(max_tokens=100))
        self.assertEqual(len(package.context_units), 1)
        self.assertTrue(any(item["reason"] == "DUPLICATE_CHUNK" for item in package.dropped_units))

    def test_same_parent_expansion_dedup(self) -> None:
        units, _ = self.expander.expand([self.ranked_child(2)], ContextPolicy())
        duplicate = ContextUnit(**{**units[0].__dict__, "context_unit_id": "duplicate"})
        package = self.builder.build([], [units[0], duplicate], ContextPolicy(max_tokens=100))
        self.assertEqual(len(package.context_units), 1)

    def test_context_hash_is_stable(self) -> None:
        units, _ = self.expander.expand([self.ranked_child(2)], ContextPolicy())
        first = self.builder.build([], units, ContextPolicy(max_tokens=100))
        second = self.builder.build([], units, ContextPolicy(max_tokens=100))
        self.assertEqual(first.context_hash, second.context_hash)


class ParentChildIntegrationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.store = ParentStore(latest_v3_artifact(), FakeTokenizer())
        cls.retriever = LocalExternalEvidenceRetriever(cls.store)
        cls.expander = ParentChildExpander(cls.store, FakeTokenizer())
        cls.builder = ContextBuilder(FakeTokenizer())

    def assert_query_hits_source(self, query: str, expected_source_id: str) -> None:
        children = self.retriever.search(query, top_k=5)
        units, _ = self.expander.expand(children, ContextPolicy(mode="child_with_neighbors"))
        package = self.builder.build([], units, ContextPolicy(mode="child_with_neighbors", max_tokens=400))
        self.assertIn(expected_source_id, package.included_source_ids)
        self.assertTrue(package.included_parent_ids)
        self.assertTrue(package.included_chunk_ids)
        self.assertTrue(all(unit.heading_path for unit in package.context_units))

    def test_who_hypertension_context(self) -> None:
        self.assert_query_hits_source("成人高血压药物治疗 收缩压≥140 mmHg", "who_hypertension_pharmacological_guideline_2021_zh")

    def test_who_stroke_context(self) -> None:
        self.assert_query_hits_source("卒中 高血压 风险 诊断 治疗", "who_stroke_fact_sheet_zh")

    def test_who_preeclampsia_context(self) -> None:
        self.assert_query_hits_source("先兆子痫 妊娠20周后 血压≥140/90 mm Hg", "who_preeclampsia_fact_sheet_zh")


if __name__ == "__main__":
    unittest.main()
