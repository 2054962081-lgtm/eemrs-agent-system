from __future__ import annotations

import unittest

from rag.ingestion.medical_semantic_chunker import ChunkingConfig, MedicalSemanticChunker
from rag.ingestion.medical_term_protector import MedicalTermProtector, is_inside_protected_span
from rag.ingestion.source_downloader import SourceDownloader
from rag.schema.medical_ingestion import DocumentBlock, ParsedDocument, SourceRecord


class FakeTokenizer:
    model_max_length = 512

    def count(self, text: str, add_special_tokens: bool = True) -> int:
        return max(1, len(text) // 2) + (2 if add_special_tokens else 0)


def source(status: str = "active") -> SourceRecord:
    return SourceRecord(
        source_id="test_source",
        title="测试指南",
        publisher="Unit Test",
        source_domain="who.int",
        source_url="https://www.who.int/zh/test",
        download_url="https://www.who.int/zh/test",
        document_type="guideline",
        language="zh",
        publication_date="2021",
        version="2021",
        retrieved_at="2026-09-05T00:00:00+00:00",
        status=status,
        superseded_by=None,
        license=None,
        license_verified=False,
        license_status="unknown",
        sha256="x",
        allowed_for_ingestion=(status == "active"),
    )


class MedicalTermProtectorTest(unittest.TestCase):
    def setUp(self) -> None:
        self.protector = MedicalTermProtector()

    def assertProtected(self, text: str, needle: str) -> None:
        spans = self.protector.detect(text)
        self.assertTrue(any(needle in span.text for span in spans), spans)

    def test_medical_terms_and_abbreviations_are_protected(self) -> None:
        self.assertProtected("急性冠脉综合征需要评估。", "急性冠脉综合征")
        self.assertProtected("HELLP综合征需要紧急处理。", "HELLP综合征")
        self.assertProtected("天冬氨酸氨基转移酶（AST）升高。", "天冬氨酸氨基转移酶")

    def test_numeric_units_are_protected(self) -> None:
        self.assertProtected("收缩压≥160 mmHg。", "160 mmHg")
        self.assertProtected("血小板低于100×10^9/L。", "100×10^9/L")
        self.assertProtected("妊娠20周后出现症状。", "20周")

    def test_boundary_inside_entity_is_detected(self) -> None:
        text = "一个完整医学实体急性冠脉综合征刚好跨target位置。"
        span = next(span for span in self.protector.detect(text) if "急性冠脉综合征" in span.text)
        self.assertIsNotNone(is_inside_protected_span(span.start + 2, [span]))


class SourceValidityGateTest(unittest.TestCase):
    def test_deprecated_source_detection(self) -> None:
        downloader = SourceDownloader()
        status, allowed, reason = downloader.validity("本标准已废止。", source_spec_stub())
        self.assertEqual(status, "deprecated")
        self.assertFalse(allowed)
        self.assertEqual(reason, "rejected_deprecated_source")


def source_spec_stub():
    from rag.ingestion.source_downloader import SourceSpec

    return SourceSpec(
        source_id="ws384",
        title="妊娠期高血压疾病诊断",
        publisher="国家卫生健康委员会",
        source_url="https://www.nhc.gov.cn/test.shtml",
        document_type="official_standard",
        language="zh",
        publication_date="2012",
        version="WS 384-2012",
        negative_test=True,
    )


class MedicalSemanticChunkerTest(unittest.TestCase):
    def make_document(self, blocks: list[DocumentBlock]) -> ParsedDocument:
        return ParsedDocument(source=source(), pages=[{"page": 1}], blocks=blocks, parser_name="test")

    def test_list_items_split_between_items(self) -> None:
        blocks = [
            DocumentBlock("b1", 1, "list_item", "a) 收缩压≥160 mmHg。", ["测试指南", "诊断标准"], 1),
            DocumentBlock("b2", 1, "list_item", "b) 血小板低于100×10^9/L。", ["测试指南", "诊断标准"], 2),
            DocumentBlock("b3", 1, "list_item", "c) 妊娠20周后出现蛋白尿。", ["测试指南", "诊断标准"], 3),
        ]
        chunker = MedicalSemanticChunker(FakeTokenizer(), ChunkingConfig(20, 4, 22, 60))
        _, chunks = chunker.chunk(self.make_document(blocks))
        self.assertGreaterEqual(len(chunks), 1)
        self.assertTrue(all("a)" in c.chunk_text or "b)" in c.chunk_text or "c)" in c.chunk_text for c in chunks))

    def test_table_rows_repeat_header(self) -> None:
        blocks = [
            DocumentBlock("h", 1, "table_row", "级别 | 定义 | 处理原则", ["测试指南", "表：急诊患者病情分级"], 1),
            DocumentBlock("r1", 1, "table_row", "A级 | 危重 | 立即处理", ["测试指南", "表：急诊患者病情分级"], 2),
            DocumentBlock("r2", 1, "table_row", "B级 | 急症 | 优先处理", ["测试指南", "表：急诊患者病情分级"], 3),
        ]
        chunker = MedicalSemanticChunker(FakeTokenizer(), ChunkingConfig(30, 4, 80, 120))
        _, chunks = chunker.chunk(self.make_document(blocks))
        self.assertTrue(chunks)
        self.assertTrue(all("级别 | 定义 | 处理原则" in chunk.chunk_text for chunk in chunks))

    def test_stable_chunk_id(self) -> None:
        blocks = [DocumentBlock("b1", 1, "paragraph", "急性冠脉综合征。", ["测试指南", "章节"], 1)]
        chunker = MedicalSemanticChunker(FakeTokenizer(), ChunkingConfig())
        _, first = chunker.chunk(self.make_document(blocks))
        _, second = chunker.chunk(self.make_document(blocks))
        self.assertEqual([chunk.chunk_id for chunk in first], [chunk.chunk_id for chunk in second])


if __name__ == "__main__":
    unittest.main()
