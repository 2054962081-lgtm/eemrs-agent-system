"""Whitelist source downloader and validity gate for Medical RAG V3."""

from __future__ import annotations

import hashlib
import json
import mimetypes
import re
import time
import unicodedata
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from urllib.parse import urljoin, urlparse

import requests
from bs4 import BeautifulSoup

from rag.rag_config import PROJECT_ROOT
from rag.schema.medical_ingestion import SourceRecord

ALLOWED_DOMAINS = ("nhc.gov.cn", "who.int")
DEPRECATED_MARKERS = ("废止", "失效", "已废止", "superseded", "withdrawn", "obsolete")


@dataclass(frozen=True)
class SourceSpec:
    source_id: str
    title: str
    publisher: str
    source_url: str
    document_type: str
    language: str
    publication_date: str | None
    version: str | None
    expected_attachment_pattern: str | None = None
    negative_test: bool = False


DEFAULT_SOURCE_SPECS = [
    SourceSpec(
        source_id="nhc_stroke_prevention_guideline_2021",
        title="中国脑卒中防治指导规范（2021年版）",
        publisher="国家卫生健康委员会",
        source_url="https://www.nhc.gov.cn/wjw/c100175/202108/df1789a9ba174678b1979bc14117b58f.shtml",
        document_type="official_guideline",
        language="zh",
        publication_date="2021-08-27",
        version="2021",
        expected_attachment_pattern="脑卒中防治指导规范",
    ),
    SourceSpec(
        source_id="nhc_emergency_critical_standard",
        title="需要紧急救治的急危重伤病标准及诊疗规范",
        publisher="国家卫生健康委员会",
        source_url="https://www.nhc.gov.cn/yzygj/c100071/201311/078d51129f724b9bb9bf7a8c49783542.shtml",
        document_type="official_clinical_standard",
        language="zh",
        publication_date="2013-09-06",
        version=None,
        expected_attachment_pattern="急危重伤病",
    ),
    SourceSpec(
        source_id="who_hypertension_pharmacological_guideline_2021_zh",
        title="成人高血压药物治疗指南",
        publisher="World Health Organization",
        source_url="https://www.who.int/zh/publications/i/item/9789240033986",
        document_type="guideline",
        language="zh",
        publication_date="2021",
        version="2021",
        expected_attachment_pattern=None,
    ),
    SourceSpec(
        source_id="who_stroke_fact_sheet_zh",
        title="卒中",
        publisher="World Health Organization",
        source_url="https://www.who.int/zh/news-room/fact-sheets/detail/stroke",
        document_type="fact_sheet",
        language="zh",
        publication_date=None,
        version=None,
        expected_attachment_pattern=None,
    ),
    SourceSpec(
        source_id="who_preeclampsia_fact_sheet_zh",
        title="先兆子痫",
        publisher="World Health Organization",
        source_url="https://www.who.int/zh/news-room/fact-sheets/detail/pre-eclampsia",
        document_type="fact_sheet",
        language="zh",
        publication_date=None,
        version=None,
        expected_attachment_pattern=None,
    ),
    SourceSpec(
        source_id="nhc_ws_384_2012_pregnancy_hypertension_deprecated",
        title="妊娠期高血压疾病诊断 WS 384-2012",
        publisher="国家卫生健康委员会",
        source_url="https://www.nhc.gov.cn/wjw/s9491/201212/34115.shtml",
        document_type="official_standard",
        language="zh",
        publication_date="2012",
        version="WS 384-2012",
        expected_attachment_pattern="WS 384",
        negative_test=True,
    ),
]


def utc_now() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat()


def is_allowed_domain(url: str) -> bool:
    host = urlparse(url).netloc.lower()
    return any(host == domain or host.endswith("." + domain) for domain in ALLOWED_DOMAINS)


def normalize_filename(name: str) -> str:
    normalized = unicodedata.normalize("NFKC", name)
    normalized = re.sub(r"[^\w.\-\u4e00-\u9fff]+", "_", normalized, flags=re.UNICODE).strip("._")
    return normalized[:140] or "downloaded_source"


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


class SourceDownloader:
    def __init__(
        self,
        root: Path | None = None,
        timeout: int = 25,
        retries: int = 2,
        max_bytes: int = 80 * 1024 * 1024,
    ):
        self.root = root or PROJECT_ROOT / "rag" / "sources"
        self.raw_dir = self.root / "raw"
        self.rejected_dir = self.root / "rejected"
        self.manifest_dir = self.root / "manifests"
        self.timeout = timeout
        self.retries = retries
        self.max_bytes = max_bytes
        self.session = requests.Session()
        self.session.headers.update(
            {"User-Agent": "eemrs-agent-system-medical-rag-v3/1.0 (+source provenance audit)"}
        )

    def fetch(self, url: str) -> requests.Response:
        if not url.lower().startswith("https://"):
            raise ValueError(f"Only HTTPS sources are allowed: {url}")
        if not is_allowed_domain(url):
            raise ValueError(f"Source domain is not whitelisted: {url}")
        last_error: Exception | None = None
        for attempt in range(self.retries + 1):
            try:
                response = self.session.get(url, timeout=self.timeout, stream=True)
                response.raise_for_status()
                return response
            except Exception as exc:
                last_error = exc
                if attempt < self.retries:
                    time.sleep(0.8 * (attempt + 1))
        raise RuntimeError(f"Failed to fetch {url}: {last_error}")

    def fetch_text(self, url: str) -> tuple[str, str]:
        response = self.fetch(url)
        content = response.content
        response.close()
        response.encoding = response.encoding or "utf-8"
        return content.decode(response.encoding, errors="replace"), response.headers.get("content-type", "")

    def find_attachment(self, html: str, page_url: str, pattern: str | None) -> str | None:
        soup = BeautifulSoup(html, "html.parser")
        candidates: list[tuple[str, str]] = []
        for link in soup.find_all("a", href=True):
            href = urljoin(page_url, link["href"])
            text = link.get_text(" ", strip=True)
            if not href.lower().startswith("https://"):
                continue
            if not is_allowed_domain(href):
                continue
            if re.search(r"\.(pdf|doc|docx|xls|xlsx|html?|shtml)(\?|$)", href, re.I) or (
                "iris.who.int/server/api/core/bitstreams/" in href and "/content" in href
            ):
                candidates.append((text, href))
        if pattern:
            for text, href in candidates:
                if pattern in text or pattern in href:
                    return href
        for text, href in candidates:
            if href.lower().endswith(".pdf") or ".pdf?" in href.lower():
                return href
        return candidates[0][1] if candidates else None

    def validity(self, html: str, spec: SourceSpec) -> tuple[str, bool, str | None]:
        lowered = html.lower()
        for marker in DEPRECATED_MARKERS:
            if marker.lower() in lowered:
                return ("deprecated", False, "rejected_deprecated_source")
        if spec.negative_test:
            return ("unknown", False, "negative_test_source_not_allowed")
        return ("active", True, None)

    def infer_license(self, html: str, spec: SourceSpec) -> tuple[str | None, bool, str]:
        lowered = html.lower()
        if "creative commons" in lowered or "cc by" in lowered or "知识共享" in html:
            return ("Creative Commons terms stated on source page", True, "verified_open")
        if spec.source_domain if hasattr(spec, "source_domain") else False:
            pass
        if "who.int" in urlparse(spec.source_url).netloc.lower():
            return ("WHO publication page license not machine-confirmed", False, "unknown")
        return (None, False, "unknown")

    def download_binary(self, url: str, destination: Path) -> tuple[int, str]:
        response = self.fetch(url)
        content_type = response.headers.get("content-type", "")
        total = 0
        destination.parent.mkdir(parents=True, exist_ok=True)
        with destination.open("wb") as handle:
            for chunk in response.iter_content(chunk_size=1024 * 256):
                if not chunk:
                    continue
                total += len(chunk)
                if total > self.max_bytes:
                    raise RuntimeError(f"Download exceeds max size {self.max_bytes}: {url}")
                handle.write(chunk)
        response.close()
        return total, content_type

    def process_spec(self, spec: SourceSpec) -> SourceRecord:
        html, page_mime = self.fetch_text(spec.source_url)
        status, allowed, rejection_reason = self.validity(html, spec)
        license_text, license_verified, license_status = self.infer_license(html, spec)
        attachment = None
        if spec.expected_attachment_pattern or spec.document_type != "fact_sheet":
            attachment = self.find_attachment(html, spec.source_url, spec.expected_attachment_pattern)
        download_url = attachment or spec.source_url
        target_dir = self.raw_dir if allowed else self.rejected_dir / "raw"
        if "iris.who.int/server/api/core/bitstreams/" in download_url and "/content" in download_url:
            ext = ".pdf"
        else:
            ext = Path(urlparse(download_url).path).suffix or mimetypes.guess_extension(page_mime.split(";")[0]) or ".html"
        filename = normalize_filename(f"{spec.source_id}{ext}")
        local_path = target_dir / filename
        if not local_path.exists():
            if attachment:
                _, mime = self.download_binary(download_url, local_path)
            else:
                target_dir.mkdir(parents=True, exist_ok=True)
                local_path.write_text(html, encoding="utf-8")
                mime = page_mime or "text/html"
        else:
            mime = mimetypes.guess_type(local_path.name)[0] or page_mime
        digest = sha256_file(local_path)
        domain = urlparse(spec.source_url).netloc.lower()
        return SourceRecord(
            source_id=spec.source_id,
            title=spec.title,
            publisher=spec.publisher,
            source_domain=domain,
            source_url=spec.source_url,
            download_url=download_url,
            document_type=spec.document_type,
            language=spec.language,
            publication_date=spec.publication_date,
            version=spec.version,
            retrieved_at=utc_now(),
            status=status,
            superseded_by=None,
            license=license_text,
            license_verified=license_verified,
            license_status=license_status,
            sha256=digest,
            allowed_for_ingestion=allowed,
            rejection_reason=rejection_reason,
            local_path=str(local_path),
            mime_type=mime,
        )

    def run(self, specs: list[SourceSpec] | None = None) -> dict[str, Any]:
        self.manifest_dir.mkdir(parents=True, exist_ok=True)
        records: list[SourceRecord] = []
        errors: list[dict[str, str]] = []
        for spec in specs or DEFAULT_SOURCE_SPECS:
            try:
                records.append(self.process_spec(spec))
            except Exception as exc:
                errors.append({"source_id": spec.source_id, "source_url": spec.source_url, "error": str(exc)})
        manifest = {
            "retrieved_at": utc_now(),
            "allowed_domains": list(ALLOWED_DOMAINS),
            "sources": [record.to_dict() for record in records],
            "errors": errors,
        }
        (self.manifest_dir / "authoritative_sources.json").write_text(
            json.dumps(manifest, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
        return manifest


def main() -> int:
    manifest = SourceDownloader().run()
    print(json.dumps(manifest, ensure_ascii=False, indent=2))
    return 1 if manifest["errors"] else 0


if __name__ == "__main__":
    raise SystemExit(main())
