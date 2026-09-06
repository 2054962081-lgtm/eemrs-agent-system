"""Parser adapters producing ParsedDocument blocks for Medical RAG V3."""

from __future__ import annotations

import importlib.util
import re
import shutil
import subprocess
import tempfile
from pathlib import Path
from typing import Any

from bs4 import BeautifulSoup

from rag.rag_config import resolve_project_path
from rag.schema.medical_ingestion import DocumentBlock, ParsedDocument, SourceRecord


class LocalDocumentParser:
    name = "local_html_pdf_parser"

    def parse(self, source: SourceRecord) -> ParsedDocument:
        path = resolve_project_path(source.local_path or "")
        suffix = path.suffix.lower()
        if suffix in {".html", ".htm", ".shtml"} or (source.mime_type or "").startswith("text/html"):
            return self.parse_html(source, path.read_text(encoding="utf-8", errors="replace"))
        if suffix == ".pdf" or "pdf" in (source.mime_type or "").lower():
            return self.parse_pdf(source, path)
        return ParsedDocument(
            source=source,
            pages=[],
            blocks=[],
            parser_name=self.name,
            parser_status="PARSER_REVIEW_REQUIRED",
            warnings=[f"unsupported_document_type:{suffix or source.mime_type}"],
        )

    def parse_html(self, source: SourceRecord, html: str) -> ParsedDocument:
        soup = BeautifulSoup(html, "html.parser")
        for tag in soup(["script", "style", "noscript", "nav", "footer"]):
            tag.decompose()
        content = (
            soup.select_one("article.sf-detail-body-wrapper")
            or soup.select_one("article.dynamic-content__publication")
            or soup.select_one("main")
            or soup
        )
        heading_path: list[str] = [source.title]
        blocks: list[DocumentBlock] = []
        order = 0
        selectors = ["h1", "h2", "h3", "h4", "p", "li", "tr"]
        for node in content.find_all(selectors):
            text = re.sub(r"\s+", " ", node.get_text(" ", strip=True)).strip()
            if not text or len(text) < 2:
                continue
            name = node.name.lower()
            if name in {"h1", "h2", "h3", "h4"}:
                level = int(name[1])
                heading_path = heading_path[:level - 1] + [text]
                block_type = "heading"
            elif name == "li":
                block_type = "list_item"
            elif name == "tr":
                cells = [re.sub(r"\s+", " ", cell.get_text(" ", strip=True)) for cell in node.find_all(["th", "td"])]
                if not cells:
                    continue
                text = " | ".join(cells)
                block_type = "table_row"
            else:
                block_type = classify_text(text)
            order += 1
            blocks.append(
                DocumentBlock(
                    block_id=f"{source.source_id}_b{order:05d}",
                    page=None,
                    block_type=block_type,
                    text=text,
                    heading_path=list(heading_path),
                    order=order,
                )
            )
        return ParsedDocument(
            source=source,
            pages=[{"page": None, "source_url": source.source_url}],
            blocks=blocks,
            parser_name=self.name,
            warnings=[] if blocks else ["html_no_blocks_extracted"],
        )

    def parse_pdf(self, source: SourceRecord, path: Path) -> ParsedDocument:
        pdftotext = shutil.which("pdftotext")
        if pdftotext:
            with tempfile.TemporaryDirectory() as tmpdir:
                txt_path = Path(tmpdir) / "document.txt"
                result = subprocess.run(
                    [pdftotext, "-layout", "-enc", "UTF-8", str(path), str(txt_path)],
                    check=False,
                    text=True,
                    capture_output=True,
                    timeout=60,
                )
                if result.returncode == 0 and txt_path.exists():
                    text = txt_path.read_text(encoding="utf-8", errors="replace")
                    blocks = []
                    heading_path = [source.title]
                    order = 0
                    for paragraph in split_paragraphs(text):
                        if re.search(r"(参考文献|References)", paragraph, re.I):
                            break
                        block_type = classify_text(paragraph)
                        if block_type == "heading":
                            heading_path = update_heading_path(heading_path, paragraph)
                        order += 1
                        blocks.append(
                            DocumentBlock(
                                block_id=f"{source.source_id}_b{order:05d}",
                                page=None,
                                block_type=block_type,
                                text=paragraph,
                                heading_path=list(heading_path),
                                order=order,
                            )
                        )
                    if blocks:
                        return ParsedDocument(
                            source=source,
                            pages=[{"page": None, "char_count": len(text)}],
                            blocks=blocks,
                            parser_name=f"{self.name}:pdftotext",
                        )
        fitz_spec = importlib.util.find_spec("fitz")
        if fitz_spec is None:
            return ParsedDocument(
                source=source,
                pages=[],
                blocks=[],
                parser_name=self.name,
                parser_status="PARSER_REVIEW_REQUIRED",
                warnings=["pdf_parser_dependency_missing: install PyMuPDF/pdfplumber or configure RAGFlow adapter"],
            )
        import fitz  # type: ignore

        doc = fitz.open(path)
        blocks: list[DocumentBlock] = []
        pages: list[dict[str, Any]] = []
        heading_path = [source.title]
        order = 0
        for page_index, page in enumerate(doc, 1):
            text = page.get_text("text")
            pages.append({"page": page_index, "char_count": len(text)})
            for paragraph in split_paragraphs(text):
                block_type = classify_text(paragraph)
                if block_type == "heading":
                    heading_path = update_heading_path(heading_path, paragraph)
                order += 1
                blocks.append(
                    DocumentBlock(
                        block_id=f"{source.source_id}_b{order:05d}",
                        page=page_index,
                        block_type=block_type,
                        text=paragraph,
                        heading_path=list(heading_path),
                        order=order,
                    )
                )
        warnings = []
        if not blocks:
            warnings.append("pdf_no_text_extracted")
        return ParsedDocument(source=source, pages=pages, blocks=blocks, parser_name=self.name, warnings=warnings)


def split_paragraphs(text: str) -> list[str]:
    rough = re.split(r"\n\s*\n|(?<=。)\s*\n", text or "")
    return [re.sub(r"\s+", " ", item).strip() for item in rough if len(item.strip()) >= 2]


def classify_text(text: str) -> str:
    stripped = text.strip()
    if len(stripped) <= 40 and re.search(r"(章|节|指南|标准|规范|诊断|治疗|定义)$", stripped):
        return "heading"
    if re.match(r"^([（(]?\d+[）)]|[a-zA-Z][).、]|[一二三四五六七八九十]+[、.])", stripped):
        return "list_item"
    if "推荐" in stripped or "建议" in stripped:
        return "recommendation"
    if "定义" in stripped:
        return "definition"
    if "|" in stripped:
        return "table_row"
    return "paragraph"


def update_heading_path(current: list[str], heading: str) -> list[str]:
    if re.match(r"^第[一二三四五六七八九十0-9]+章", heading):
        return current[:1] + [heading]
    if re.match(r"^第[一二三四五六七八九十0-9]+节", heading):
        return current[:2] + [heading]
    return current[:3] + [heading]
