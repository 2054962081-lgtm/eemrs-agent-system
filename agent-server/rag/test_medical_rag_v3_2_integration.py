from __future__ import annotations

import argparse
import json
import tempfile
import unittest
from pathlib import Path

from rag.context.context_builder import ContextBuilder, ParentChildExpander
from rag.context.context_models import ContextPolicy
from rag.context.external_evidence_pipeline import ExternalEvidencePipeline
from rag.ingestion.build_external_v3_collection import build_collection
from rag.ingestion.chunk_quality_audit import audit_chunks
from rag.ingestion.document_cleaner import DocumentCleaner
from rag.ingestion.medical_semantic_chunker import MedicalSemanticChunker
from rag.retrieval.dense_retriever import DenseRetriever
from rag.retrieval.fusion import fuse_rrf
from rag.retrieval.hybrid_retriever import HybridRetriever
from rag.retrieval.reranker import CrossEncoderReranker
from rag.retrieval.sparse_retriever import SparseRetriever
from rag.schema.medical_ingestion import DocumentBlock, ParsedDocument, SourceRecord
from rag.store.parent_store import ParentStore, latest_v3_artifact


def source() -> SourceRecord:
    return SourceRecord(
        source_id="who_test",
        title="WHO测试指南",
        publisher="World Health Organization",
        source_domain="who.int",
        source_url="https://www.who.int/test",
        download_url=None,
        document_type="guideline",
        language="zh",
        publication_date="2026",
        version="2026",
        retrieved_at="2026-09-06T00:00:00+00:00",
        status="active",
        superseded_by=None,
        license="CC BY",
        license_verified=True,
        license_status="verified_open",
        sha256="abc",
        allowed_for_ingestion=True,
    )


def block(text: str, order: int, block_type: str = "paragraph", page: int | None = None) -> DocumentBlock:
    return DocumentBlock(
        block_id=f"b{order}",
        page=page,
        block_type=block_type,
        text=text,
        heading_path=["WHO测试指南", "治疗"],
        order=order,
    )


class MedicalRagV32IntegrationTest(unittest.TestCase):
    def test_cleaner_filters_copyright_toc_navigation_and_repeated_header_footer(self):
        doc = ParsedDocument(
            source=source(),
            pages=[{"page": 1}, {"page": 2}, {"page": 3}],
            blocks=[
                block("© World Health Organization 2022 All rights reserved ISBN 978-1", 1, page=1),
                block("目录 第一章 .... 1 第二章 .... 2 第三章 .... 3", 2, page=1),
                block("Home | Newsroom | Fact sheets | Health topics | Menu", 3),
                block("WHO测试指南", 4, page=1),
                block("WHO测试指南", 5, page=2),
                block("WHO测试指南", 6, page=3),
                block("世卫组织建议成人高血压患者在医生评估后接受治疗。", 7),
            ],
            parser_name="test",
        )

        cleaned, decisions, summary = DocumentCleaner().clean(doc)

        removed = {decision.reason for decision in decisions if decision.removed}
        self.assertIn("COPYRIGHT", removed)
        self.assertIn("TABLE_OF_CONTENTS", removed)
        self.assertIn("NAVIGATION", removed)
        self.assertIn("HEADER_FOOTER", removed)
        self.assertEqual([item.text for item in cleaned.blocks], ["世卫组织建议成人高血压患者在医生评估后接受治疗。"])
        self.assertEqual(summary["cleaner_status"], "OK")

    def test_cleaner_does_not_remove_who_recommends_clinical_text(self):
        doc = ParsedDocument(
            source=source(),
            pages=[],
            blocks=[
                block("WHO recommends pharmacological treatment for adults with hypertension when clinically indicated.", 1),
                block("根据妊娠20周后高血压（血压≥140/90 mm Hg）和蛋白尿诊断先兆子痫。", 2),
            ],
            parser_name="test",
        )

        cleaned, _decisions, _summary = DocumentCleaner().clean(doc)

        self.assertEqual(len(cleaned.blocks), 2)

    def test_cleaned_parent_child_refs_and_term_quality(self):
        doc = ParsedDocument(
            source=source(),
            pages=[],
            blocks=[
                block("© World Health Organization 2022 All rights reserved ISBN 978-1", 1),
                block("世卫组织建议成人高血压患者关注血压≥140/90 mm Hg，并由医生评估治疗。", 2),
            ],
            parser_name="test",
        )
        cleaned, _decisions, _summary = DocumentCleaner().clean(doc)

        parents, chunks = MedicalSemanticChunker().chunk(cleaned)
        parent_ids = {parent.parent_id for parent in parents}
        quality, _details = audit_chunks(chunks, [source()])

        self.assertTrue(chunks)
        self.assertTrue(all(chunk.parent_id in parent_ids for chunk in chunks))
        self.assertEqual(quality["protected_span_split_violation_count"], 0)
        self.assertEqual(quality["numeric_unit_split_violation_count"], 0)

    def test_build_external_collection_dry_run_does_not_write(self):
        artifact = latest_v3_artifact()
        with tempfile.TemporaryDirectory() as tmp:
            args = argparse.Namespace(
                artifact_path=str(artifact),
                output_dir=str(Path(tmp) / "collection"),
                collection_name="medical_rag_external_v3_test",
                batch_size=2,
                dry_run=True,
                reset=False,
            )

            result = build_collection(args)

            self.assertEqual(result["health"]["status"], "DRY_RUN_ONLY")
            self.assertEqual(result["health"]["inserted_count"], 0)
            self.assertTrue((Path(tmp) / "collection" / "collection_manifest.json").exists())

    def test_v3_reuses_existing_retrieval_classes(self):
        self.assertIs(ExternalEvidencePipeline.__init__.__globals__["DenseRetriever"], DenseRetriever)
        self.assertIs(ExternalEvidencePipeline.__init__.__globals__["SparseRetriever"], SparseRetriever)
        self.assertIs(ExternalEvidencePipeline.__init__.__globals__["HybridRetriever"], HybridRetriever)
        self.assertEqual(fuse_rrf.__module__, "rag.retrieval.fusion")
        self.assertEqual(CrossEncoderReranker.__module__, "rag.retrieval.reranker")

    def test_parent_expansion_and_context_budget_for_cleaned_artifact(self):
        store = ParentStore(latest_v3_artifact())
        child = next(item.with_rank(1, 1.0) for item in store.children if item.source_id == "who_stroke_fact_sheet_zh")
        units, trace = ParentChildExpander(store).expand([child], ContextPolicy(mode="child_with_neighbors", max_tokens=220))
        package = ContextBuilder().build([], units, ContextPolicy(mode="child_with_neighbors", max_tokens=220))

        self.assertEqual(trace["input_child_count"], 1)
        self.assertLessEqual(package.token_count, 220)
        self.assertTrue(package.included_chunk_ids or package.dropped_units)


if __name__ == "__main__":
    unittest.main()
