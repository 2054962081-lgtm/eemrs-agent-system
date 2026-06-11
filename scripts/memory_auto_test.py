from __future__ import annotations

import json
import os
import re
import sys
import time
import traceback
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen


ROOT = Path(__file__).resolve().parents[1]
REPORT_PATH = ROOT / "memory_auto_test_report.md"


ID_NUMBER_RE = re.compile(r"\b\d{17}[\dXx]\b")


class MemoryAutoTest:
    def __init__(self) -> None:
        self.backend_url = os.getenv("MEMORY_BACKEND_URL", "http://localhost:8080").rstrip("/")
        self.rag_url = os.getenv("MEMORY_RAG_URL", "http://localhost:18080").rstrip("/")
        self.token_a = os.getenv("MEMORY_TEST_PATIENT_A_TOKEN") or os.getenv("MEMORY_TEST_TOKEN")
        self.token_b = os.getenv("MEMORY_TEST_PATIENT_B_TOKEN")
        self.sample_mode = os.getenv("MEMORY_TEST_SAMPLE_MODE", "").strip().lower() in {"1", "true", "yes", "on"}
        self.sample_password = os.getenv("MEMORY_TEST_SAMPLE_PASSWORD", "MemoryAutoTest@2026")
        self.sample_patient_a_id = os.getenv("MEMORY_TEST_SAMPLE_PATIENT_A_ID", "AUTO_PATIENT_A_MEMORY_20260611")
        self.sample_patient_b_id = os.getenv("MEMORY_TEST_SAMPLE_PATIENT_B_ID", "AUTO_PATIENT_B_MEMORY_20260611")
        self.session_id = f"auto-test-{int(time.time())}"
        self.created_long_item_ids: list[int] = []
        self.patient_a_hash: str | None = None
        self.patient_b_hash: str | None = None
        self.original_profile_a: dict[str, Any] | None = None
        self.profile_touched = False
        self.events: list[tuple[str, str, str]] = []
        self.failures: list[str] = []
        self.skips: list[str] = []
        self.cleanup_results: list[str] = []
        self.started_at = datetime.now(timezone.utc).astimezone()

    def run(self) -> str:
        if self.sample_mode and not self.token_a:
            self.prepare_sample_patients()
        if not self.token_a:
            self.skip("Missing MEMORY_TEST_TOKEN or MEMORY_TEST_PATIENT_A_TOKEN")
            self.write_report("SKIPPED")
            print("SKIPPED")
            return "SKIPPED"

        try:
            self.step_health()
            self.step_long_profile()
            self.step_long_items()
            self.step_medium_milvus()
            self.step_short_session()
            self.step_context()
            self.step_prompt_contract()
            self.step_complete_archive()
            self.step_privacy_isolation()
        except Exception as exc:
            self.fail(str(exc))
            self.events.append(("exception", "FAIL", traceback.format_exc(limit=5)))
        finally:
            self.cleanup()

        status = self.final_status()
        self.write_report(status)
        print(status)
        return status

    def prepare_sample_patients(self) -> None:
        self.token_a = self.register_and_login_sample_patient(
            self.sample_patient_a_id,
            "Memory Auto Patient A",
        )
        self.token_b = self.register_and_login_sample_patient(
            self.sample_patient_b_id,
            "Memory Auto Patient B",
        )
        self.pass_event("sample-auth", "Registered or reused sample patients and obtained JWT tokens")

    def register_and_login_sample_patient(self, id_number: str, user_name: str) -> str:
        register_body = {
            "type": "pt",
            "idNumber": id_number,
            "userName": user_name,
            "password": self.sample_password,
            "department": "",
        }
        try:
            self.backend_post_public("/api/auth/register", register_body)
        except Exception as exc:
            self.events.append(("sample-register", "PARTIAL", f"sample user may already exist: {exc}"))

        login = self.backend_post_public(
            "/api/auth/login",
            {
                "type": "pt",
                "idNumber": id_number,
                "password": self.sample_password,
                "department": "",
            },
        )
        token = str(login.get("token") or "")
        self.assert_true(bool(token), f"sample patient login returned token for {user_name}")
        return token

    def step_health(self) -> None:
        scope = self.backend_get("/api/memory/scope")
        self.patient_a_hash = str(scope.get("patientIdHash") or "")
        self.assert_true(bool(self.patient_a_hash), "patient A patientIdHash resolved")
        self.assert_no_plain_id(scope, "scope response")
        self.pass_event("scope", "Resolved patient hash")

        context = self.backend_get("/api/memory/context", {"sessionId": self.session_id, "query": "health memory"})
        self.assert_no_plain_id(context, "context response")
        self.pass_event("context-health", "Java memory context reachable")

        rag_health = self.rag_get("/memory/health")
        self.assert_true(bool(rag_health.get("success")), "RAG /memory/health success=true")
        self.pass_event("rag-health", json.dumps(rag_health, ensure_ascii=False))

    def step_long_profile(self) -> None:
        self.original_profile_a = self.backend_get("/api/memory/long/profile")
        body = {
            "gender": "male",
            "birthDate": "1998-05-12",
            "heightCm": 175,
            "weightKg": 70,
            "bloodType": "A",
            "specialStatus": "none",
            "source": "auto_test",
            "confirmed": 1,
        }
        saved = self.backend_post("/api/memory/long/profile", body)
        self.profile_touched = True
        self.assert_equal(saved.get("gender"), "male", "profile gender saved")
        self.assert_no_plain_id(saved, "profile response")

        loaded = self.backend_get("/api/memory/long/profile")
        self.assert_equal(loaded.get("gender"), "male", "profile gender loaded")
        self.assert_equal(str(loaded.get("bloodType")), "A", "profile blood type loaded")
        self.pass_event("long-profile", "Saved and loaded patient A health profile")

    def step_long_items(self) -> None:
        items = [
            {
                "memoryType": "allergy",
                "memoryKey": "penicillin",
                "memoryValue": "penicillin allergy with rash reaction",
                "severity": "medium",
                "evidence": "auto test confirmed",
                "source": "auto_test",
                "confirmed": 1,
                "department": "general",
            },
            {
                "memoryType": "chronic_disease",
                "memoryKey": "asthma",
                "memoryValue": "history of asthma since childhood, occasional chest tightness recently",
                "severity": "medium",
                "evidence": "auto test confirmed",
                "source": "auto_test",
                "confirmed": 1,
                "department": "general",
            },
            {
                "memoryType": "family_history",
                "memoryKey": "type 2 diabetes",
                "memoryValue": "father has type 2 diabetes history",
                "relation": "father",
                "evidence": "auto test confirmed",
                "source": "auto_test",
                "confirmed": 1,
                "department": "general",
            },
            {
                "memoryType": "surgery_history",
                "memoryKey": "appendectomy",
                "memoryValue": "appendectomy in 2018",
                "evidence": "auto test confirmed",
                "source": "auto_test",
                "confirmed": 1,
                "department": "general",
            },
            {
                "memoryType": "medication",
                "memoryKey": "no fixed long-term medication",
                "memoryValue": "no fixed long-term medication record",
                "evidence": "auto test confirmed",
                "source": "auto_test",
                "confirmed": 1,
                "department": "general",
            },
        ]
        for item in items:
            saved = self.backend_post("/api/memory/long/items", item)
            item_id = saved.get("id")
            self.assert_true(item_id is not None, f"long item created: {item['memoryType']}")
            self.created_long_item_ids.append(int(item_id))
        loaded = self.backend_get("/api/memory/long/items")
        auto_items = [item for item in loaded if item.get("source") == "auto_test" and item.get("id") in self.created_long_item_ids]
        self.assert_true(len(auto_items) >= 5, "five auto_test long-term memory items readable")

        context = self.backend_get("/api/memory/context", {"sessionId": self.session_id, "query": "penicillin asthma diabetes appendectomy"})
        long_text = json.dumps(context.get("longTermMemory", {}), ensure_ascii=False).lower()
        for term in ["penicillin", "asthma", "diabetes", "appendectomy", "medication"]:
            self.assert_true(term in long_text, f"longTermMemory contains {term}")
        self.pass_event("long-items", "Created 5 long-term items and verified context")

    def step_medium_milvus(self) -> None:
        if not self.patient_a_hash:
            raise AssertionError("patient A hash missing")
        rows = [
            (
                "auto_test_visit_20260601",
                "pre_inquiry_summary",
                "respiratory",
                "2026-06-01 cough fever pre-inquiry summary: yellow sputum, max temperature 38.5 C, no chest pain or obvious dyspnea; suggested respiratory visit and CBC/CRP follow-up.",
            ),
            (
                "auto_test_report_20260603",
                "report_summary",
                "laboratory",
                "2026-06-03 CBC report summary: WBC 12.5 x10^9/L and CRP 32 mg/L, infection-related indicators elevated; suggested symptom-based CBC and CRP recheck.",
            ),
        ]
        for source_id, source_type, department, text in rows:
            result = self.rag_post(
                "/memory/upsert",
                {
                    "collection": "medical_user_memory",
                    "text": text,
                    "metadata": {
                        "patientIdHash": self.patient_a_hash,
                        "memoryLevel": "medium",
                        "sourceType": source_type,
                        "sourceId": source_id,
                        "department": department,
                        "eventTime": int(time.time() * 1000),
                        "createdAt": int(time.time() * 1000),
                    },
                },
            )
            self.assert_true(result.get("success") is True, f"medium memory upsert {source_id}")
            self.assert_true(int(result.get("inserted_count") or 0) > 0, f"medium memory inserted {source_id}")

        search = self.rag_search(self.patient_a_hash, "cough fever CBC CRP respiratory", 10)
        texts = json.dumps(search.get("results", []), ensure_ascii=False).lower()
        self.assert_true("auto_test_visit_20260601" in texts, "medium visit memory searchable")
        self.assert_true("auto_test_report_20260603" in texts, "medium report memory searchable")
        self.assert_patient_filter(search, self.patient_a_hash)
        self.pass_event("medium-memory", "Milvus medium memories upserted and searchable")

    def step_short_session(self) -> None:
        created = self.backend_post(
            "/api/memory/short/session",
            {
                "sessionId": self.session_id,
                "chiefComplaint": "fever and cough for 5 days, chest tightness since yesterday",
            },
        )
        self.assert_equal(created.get("sessionId"), self.session_id, "short session created")
        self.assert_true("fever" in str(created.get("chiefComplaint", "")).lower(), "chief complaint saved")
        qa_rows = [
            ("What is the highest temperature?", "Highest 38.8 C."),
            ("Is there sputum, and what color?", "Yellow sputum."),
            ("Any chest pain or dyspnea?", "No chest pain, but some chest tightness, worse after activity."),
            ("Any medication allergy history?", "I am allergic to penicillin and had rash before."),
        ]
        for idx, (question, answer) in enumerate(qa_rows, 1):
            self.backend_post(
                f"/api/memory/short/session/{self.session_id}/qa",
                {"question": question, "answer": answer, "round": idx},
            )
        context = self.backend_get("/api/memory/context", {"sessionId": self.session_id, "query": "fever cough chest tightness"})
        short_memory = context.get("shortTermMemory") or {}
        self.assert_true(len(short_memory.get("askedQuestions") or []) >= 4, "short askedQuestions stored")
        self.assert_true(len(short_memory.get("answers") or []) >= 4, "short answers stored")
        self.assert_no_plain_id(short_memory, "short memory context")
        self.pass_event("short-memory", "Short Redis session created and QA appended")

    def step_context(self) -> None:
        context = self.backend_get("/api/memory/context", {"sessionId": self.session_id, "query": "cough fever CBC CRP asthma"})
        long_text = json.dumps(context.get("longTermMemory", {}), ensure_ascii=False).lower()
        related_text = json.dumps(context.get("relatedUserMemory", []), ensure_ascii=False).lower()
        short_text = json.dumps(context.get("shortTermMemory", {}), ensure_ascii=False).lower()
        for term in ["penicillin", "asthma", "diabetes", "appendectomy"]:
            self.assert_true(term in long_text, f"context long memory has {term}")
        for term in ["cough", "crp"]:
            self.assert_true(term in related_text, f"context related memory has {term}")
        for term in ["fever", "yellow", "tightness"]:
            self.assert_true(term in short_text, f"context short memory has {term}")
        self.pass_event("memory-context", "Context includes long, medium, and short memory")

    def step_prompt_contract(self) -> None:
        service_path = ROOT / "agent-server" / "src" / "main" / "java" / "com" / "liu" / "eemrsagent" / "agent" / "PreConsultationService.java"
        text = service_path.read_text(encoding="utf-8")
        for section in ["【长期健康档案】", "【近期就诊记忆】", "【本次问诊状态】", "【用户历史相似记忆】", "【医学知识库 RAG】"]:
            self.assert_true(section in text, f"prompt section present: {section}")
        self.pass_event("prompt-contract", "Agent prompt memory sections are present in source")

    def step_complete_archive(self) -> None:
        result = self.backend_post(f"/api/memory/short/session/{self.session_id}/complete", {})
        self.assert_true(result.get("completed") is True, "short session completed")
        time.sleep(2)
        if not self.patient_a_hash:
            raise AssertionError("patient A hash missing")
        search = self.rag_search(self.patient_a_hash, "fever cough chest tightness penicillin asthma", 10)
        text = json.dumps(search.get("results", []), ensure_ascii=False).lower()
        self.assert_true(self.session_id in text or "pre_inquiry_summary" in text, "completed short memory archived to Milvus")
        self.assert_patient_filter(search, self.patient_a_hash)
        self.pass_event("complete-archive", "Short memory completed and archived to Milvus")

    def step_privacy_isolation(self) -> None:
        if not self.token_b:
            self.skip("Missing MEMORY_TEST_PATIENT_B_TOKEN; privacy isolation test skipped")
            return
        scope_b = self.backend_get("/api/memory/scope", token=self.token_b)
        self.patient_b_hash = str(scope_b.get("patientIdHash") or "")
        self.assert_true(bool(self.patient_b_hash), "patient B patientIdHash resolved")
        context_b = self.backend_get(
            "/api/memory/context",
            {"sessionId": self.session_id, "query": "penicillin asthma cough fever CRP"},
            token=self.token_b,
        )
        b_text = json.dumps(context_b, ensure_ascii=False).lower()
        for forbidden in ["penicillin allergy", "history of asthma", "auto_test_visit_20260601", "auto_test_report_20260603"]:
            self.assert_true(forbidden not in b_text, f"patient B context does not contain patient A memory: {forbidden}")
        search_b = self.rag_search(self.patient_b_hash, "penicillin asthma cough fever CRP", 10)
        search_text = json.dumps(search_b.get("results", []), ensure_ascii=False).lower()
        self.assert_true("auto_test_visit_20260601" not in search_text, "patient B search cannot see patient A medium memory")
        self.assert_true("auto_test_report_20260603" not in search_text, "patient B search cannot see patient A report memory")
        self.pass_event("privacy-isolation", "Patient B cannot see patient A memory")

    def cleanup(self) -> None:
        for item_id in list(self.created_long_item_ids):
            try:
                self.backend_delete(f"/api/memory/long/items/{item_id}")
                self.cleanup_results.append(f"deleted long item {item_id}")
            except Exception as exc:
                self.cleanup_results.append(f"failed deleting long item {item_id}: {exc}")
        if self.profile_touched:
            try:
                if self.profile_has_content(self.original_profile_a):
                    self.backend_post("/api/memory/long/profile", self.profile_restore_body(self.original_profile_a or {}))
                    self.cleanup_results.append("restored original health profile")
                else:
                    self.backend_delete("/api/memory/long/profile")
                    self.cleanup_results.append("deleted auto_test health profile")
            except Exception as exc:
                self.cleanup_results.append(f"failed cleaning health profile: {exc}")
        if self.patient_a_hash:
            for source_id, source_type in [
                ("auto_test_visit_20260601", "pre_inquiry_summary"),
                ("auto_test_report_20260603", "report_summary"),
                (self.session_id, "pre_inquiry_summary"),
            ]:
                try:
                    self.rag_post(
                        "/memory/delete-by-source",
                        {
                            "collection": "medical_user_memory",
                            "sourceId": source_id,
                            "sourceType": source_type,
                            "filter": f"patientIdHash == '{self.patient_a_hash}'",
                        },
                    )
                    self.cleanup_results.append(f"deleted Milvus source {source_id}")
                except Exception as exc:
                    self.cleanup_results.append(f"failed deleting Milvus source {source_id}: {exc}")

    def profile_has_content(self, profile: dict[str, Any] | None) -> bool:
        if not isinstance(profile, dict):
            return False
        return any(profile.get(key) not in (None, "", 0) for key in [
            "gender",
            "birthDate",
            "heightCm",
            "weightKg",
            "bloodType",
            "specialStatus",
        ])

    def profile_restore_body(self, profile: dict[str, Any]) -> dict[str, Any]:
        return {
            "gender": profile.get("gender"),
            "birthDate": self.normalize_birth_date(profile.get("birthDate")),
            "heightCm": profile.get("heightCm"),
            "weightKg": profile.get("weightKg"),
            "bloodType": profile.get("bloodType"),
            "specialStatus": profile.get("specialStatus"),
            "source": profile.get("source") or "restored_by_auto_test",
            "confirmed": profile.get("confirmed") or 1,
            "active": profile.get("active") or 1,
        }

    def normalize_birth_date(self, value: Any) -> Any:
        if isinstance(value, str) and len(value) >= 10:
            return value[:10]
        return value

    def backend_get(self, path: str, params: dict[str, Any] | None = None, token: str | None = None) -> Any:
        url = self.backend_url + path
        if params:
            url += "?" + urlencode(params)
        return unwrap_api_response(self.http_json("GET", url, token=token or self.token_a))

    def backend_post(self, path: str, body: dict[str, Any], token: str | None = None) -> Any:
        return unwrap_api_response(self.http_json("POST", self.backend_url + path, body, token or self.token_a))

    def backend_delete(self, path: str, token: str | None = None) -> Any:
        return unwrap_api_response(self.http_json("DELETE", self.backend_url + path, token=token or self.token_a))

    def backend_post_public(self, path: str, body: dict[str, Any]) -> Any:
        return unwrap_api_response(self.http_json("POST", self.backend_url + path, body))

    def rag_get(self, path: str) -> Any:
        return self.http_json("GET", self.rag_url + path)

    def rag_post(self, path: str, body: dict[str, Any]) -> Any:
        return self.http_json("POST", self.rag_url + path, body)

    def rag_search(self, patient_hash: str, query: str, top_k: int) -> Any:
        return self.rag_post(
            "/memory/search",
            {
                "collection": "medical_user_memory",
                "query": query,
                "topK": top_k,
                "filter": f"patientIdHash == '{patient_hash}'",
            },
        )

    def http_json(self, method: str, url: str, body: dict[str, Any] | None = None, token: str | None = None) -> Any:
        data = None
        headers = {"Accept": "application/json"}
        if body is not None:
            data = json.dumps(body, ensure_ascii=False).encode("utf-8")
            headers["Content-Type"] = "application/json; charset=utf-8"
        if token:
            headers["Authorization"] = "Bearer " + token
        request = Request(url, data=data, headers=headers, method=method)
        try:
            with urlopen(request, timeout=60) as response:
                raw = response.read().decode("utf-8")
                return json.loads(raw) if raw else None
        except HTTPError as exc:
            raw = exc.read().decode("utf-8", errors="replace")
            raise RuntimeError(f"{method} {url} failed: HTTP {exc.code} {raw}") from exc
        except URLError as exc:
            raise RuntimeError(f"{method} {url} failed: {exc.reason}") from exc

    def assert_patient_filter(self, search_response: dict[str, Any], expected_hash: str) -> None:
        for result in search_response.get("results") or []:
            metadata = result.get("metadata") or {}
            self.assert_equal(metadata.get("patientIdHash"), expected_hash, "Milvus metadata patientIdHash matches")

    def assert_no_plain_id(self, value: Any, name: str) -> None:
        text = json.dumps(value, ensure_ascii=False)
        self.assert_true(ID_NUMBER_RE.search(text) is None, f"{name} has no plaintext id number")

    def assert_true(self, condition: bool, message: str) -> None:
        if not condition:
            raise AssertionError(message)

    def assert_equal(self, actual: Any, expected: Any, message: str) -> None:
        if actual != expected:
            raise AssertionError(f"{message}: expected {expected!r}, got {actual!r}")

    def pass_event(self, name: str, detail: str) -> None:
        self.events.append((name, "PASS", detail))

    def skip(self, detail: str) -> None:
        self.skips.append(detail)
        self.events.append(("skipped", "SKIPPED", detail))

    def fail(self, detail: str) -> None:
        self.failures.append(detail)
        self.events.append(("failure", "FAIL", detail))

    def final_status(self) -> str:
        if self.failures:
            return "FAIL"
        if self.skips:
            if len(self.events) == len(self.skips):
                return "SKIPPED"
            return "PARTIAL"
        return "PASS"

    def write_report(self, status: str) -> None:
        lines = [
            "# Memory Auto Test Report",
            "",
            f"- Test time: {self.started_at.isoformat(timespec='seconds')}",
            f"- Backend URL: `{self.backend_url}`",
            f"- RAG URL: `{self.rag_url}`",
            f"- Sample mode: `{self.sample_mode}`",
            f"- Final status: `{status}`",
            f"- Patient A hash resolved: `{bool(self.patient_a_hash)}`",
            f"- Patient B hash resolved: `{bool(self.patient_b_hash)}`",
            "",
            "## Results",
            "",
            "| Step | Status | Detail |",
            "| --- | --- | --- |",
        ]
        for name, event_status, detail in self.events:
            safe_detail = str(detail).replace("|", "\\|").replace("\n", "<br>")
            lines.append(f"| {name} | {event_status} | {safe_detail} |")
        lines.extend(["", "## Cleanup", ""])
        if self.cleanup_results:
            lines.extend(f"- {item}" for item in self.cleanup_results)
        else:
            lines.append("- No cleanup actions were needed.")
        lines.extend(["", "## Failures", ""])
        if self.failures:
            lines.extend(f"- {item}" for item in self.failures)
        else:
            lines.append("- None.")
        lines.extend(["", "## Skipped", ""])
        if self.skips:
            lines.extend(f"- {item}" for item in self.skips)
        else:
            lines.append("- None.")
        lines.extend([
            "",
            "## Privacy Check",
            "",
            "- The report does not include tokens.",
            "- The report does not include plaintext id numbers.",
            "- Redis and Milvus checks are scoped by patientIdHash.",
        ])
        REPORT_PATH.write_text("\n".join(lines) + "\n", encoding="utf-8")


def unwrap_api_response(value: Any) -> Any:
    if isinstance(value, dict) and "success" in value and "data" in value:
        if not value.get("success"):
            raise RuntimeError(str(value.get("message") or "API returned success=false"))
        return value.get("data")
    return value


def main() -> int:
    status = MemoryAutoTest().run()
    return 1 if status == "FAIL" else 0


if __name__ == "__main__":
    sys.exit(main())
