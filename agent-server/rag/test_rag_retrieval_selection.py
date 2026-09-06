from __future__ import annotations

import json
import unittest
from pathlib import Path

from rag.rag_api_server import select_retrieval_chunks


ROOT = Path(__file__).resolve().parents[1]
KNOWLEDGE = ROOT / "rag_knowledge"


def load_doc(relative_path: str, score: float) -> tuple[float, dict]:
    with (KNOWLEDGE / relative_path).open("r", encoding="utf-8") as handle:
        doc = json.load(handle)
    return score, {
        "chunk_id": doc["doc_id"].replace("_001", "_chunk_001"),
        "doc_id": doc["doc_id"],
        "doc_type": doc["doc_type"],
        "title": doc["title"],
        "urgency_level": doc.get("urgency_level"),
        "related_departments": "；".join(doc.get("related_departments", [])),
        "applicable_population": "；".join(doc.get("applicable_population", [])),
        "related_symptoms": "；".join(doc.get("related_symptoms", [])),
        "content_json": json.dumps(doc, ensure_ascii=False),
        "chunk_text": doc.get("chunk_text"),
    }


def chunk_ids(selected: list[tuple[float, float, dict, str, str]]) -> list[str]:
    return [entity["chunk_id"] for _, _, entity, _, _ in selected]


def doc_type_counts(meta: dict) -> dict[str, int]:
    return meta["final_doc_type_counts"]


class RagRetrievalSelectionTest(unittest.TestCase):
    def select(self, query: str, candidates: list[tuple[float, dict]], top_k: int = 8):
        return select_retrieval_chunks(query, candidates, "pre_inquiry", None, top_k)

    def test_c001_abdominal_noise_is_filtered_and_quota_is_not_mandatory(self):
        query = "主诉: 腹痛。已知: 持续1天 / 中等疼痛 / 逐渐加重 / 否认明显伴随症状。任务: 预问诊分诊、风险识别和科室推荐"
        candidates = [
            load_doc("02_red_flags/chest_pain_red_flags.json", 0.91),
            load_doc("02_red_flags/pregnancy_red_flags.json", 0.90),
            load_doc("01_symptom_inquiry/abdominal_pain.json", 0.89),
            load_doc("01_symptom_inquiry/urinary_symptoms.json", 0.88),
            load_doc("03_special_population/pregnancy_general.json", 0.87),
            load_doc("03_special_population/chemotherapy_patient.json", 0.86),
            load_doc("04_department_triage/chest_pain_triage.json", 0.85),
            load_doc("05_medical_record_templates/abdominal_pain_record_template.json", 0.84),
        ]

        selected, meta = self.select(query, candidates)

        ids = chunk_ids(selected)
        self.assertEqual(ids, ["SYM_ABDOMINAL_PAIN_chunk_001", "MR_ABDOMINAL_PAIN_chunk_001"])
        self.assertEqual(doc_type_counts(meta), {"symptom_inquiry": 1, "medical_record_template": 1})
        self.assertEqual(doc_type_counts(meta).get("red_flag", 0), 0)
        self.assertEqual(doc_type_counts(meta).get("special_population", 0), 0)
        self.assertEqual(doc_type_counts(meta).get("department_triage", 0), 0)
        self.assertGreaterEqual(meta["filtered_by_reason"].get("TOPIC_MISMATCH", 0), 3)
        self.assertGreaterEqual(meta["filtered_by_reason"].get("INACTIVE_SPECIAL_POPULATION", 0), 2)

    def test_chest_pain_keeps_chest_documents(self):
        selected, _ = self.select("胸痛胸闷持续半小时，伴大汗，任务: 预问诊分诊", [
            load_doc("02_red_flags/chest_pain_red_flags.json", 0.82),
            load_doc("04_department_triage/chest_pain_triage.json", 0.80),
            load_doc("01_symptom_inquiry/chest_pain.json", 0.78),
            load_doc("01_symptom_inquiry/abdominal_pain.json", 0.90),
        ])

        ids = chunk_ids(selected)
        self.assertIn("RED_CHEST_PAIN_chunk_001", ids)
        self.assertIn("TRIAGE_CHEST_PAIN_chunk_001", ids)
        self.assertIn("SYM_CHEST_PAIN_chunk_001", ids)
        self.assertNotIn("SYM_ABDOMINAL_PAIN_chunk_001", ids)

    def test_abdominal_pain_with_urinary_symptoms_allows_urinary_documents(self):
        selected, _ = self.select("腹痛伴尿频尿痛，持续1天，任务: 预问诊分诊", [
            load_doc("01_symptom_inquiry/abdominal_pain.json", 0.82),
            load_doc("01_symptom_inquiry/urinary_symptoms.json", 0.80),
            load_doc("04_department_triage/urinary_triage.json", 0.79),
            load_doc("02_red_flags/chest_pain_red_flags.json", 0.91),
        ])

        ids = chunk_ids(selected)
        self.assertIn("SYM_ABDOMINAL_PAIN_chunk_001", ids)
        self.assertIn("SYM_URINARY_chunk_001", ids)
        self.assertIn("TRIAGE_URINARY_chunk_001", ids)
        self.assertNotIn("RED_CHEST_PAIN_chunk_001", ids)

    def test_pregnant_abdominal_pain_activates_pregnancy_population_documents(self):
        selected, _ = self.select("怀孕10周腹痛，伴少量阴道出血，任务: 预问诊分诊", [
            load_doc("01_symptom_inquiry/abdominal_pain.json", 0.82),
            load_doc("02_red_flags/pregnancy_red_flags.json", 0.80),
            load_doc("03_special_population/pregnancy_general.json", 0.79),
            load_doc("03_special_population/chemotherapy_patient.json", 0.95),
        ])

        ids = chunk_ids(selected)
        self.assertIn("RED_PREGNANCY_chunk_001", ids)
        self.assertIn("POP_PREGNANCY_GENERAL_chunk_001", ids)
        self.assertNotIn("POP_CHEMO_chunk_001", ids)

    def test_cough_does_not_pull_unrelated_symptom_bulk(self):
        selected, _ = self.select("咳嗽三天，有痰，无胸痛，任务: 预问诊分诊", [
            load_doc("01_symptom_inquiry/cough.json", 0.78),
            load_doc("01_symptom_inquiry/abdominal_pain.json", 0.93),
            load_doc("01_symptom_inquiry/urinary_symptoms.json", 0.92),
            load_doc("02_red_flags/chest_pain_red_flags.json", 0.91),
        ])

        self.assertEqual(chunk_ids(selected), ["SYM_COUGH_chunk_001"])

    def test_chemo_fever_activates_chemo_population(self):
        selected, _ = self.select("化疗后发热38.5度伴寒战，任务: 预问诊分诊", [
            load_doc("03_special_population/chemotherapy_patient.json", 0.78),
            load_doc("02_red_flags/immunocompromised_fever_red_flags.json", 0.77),
            load_doc("01_symptom_inquiry/fever.json", 0.76),
            load_doc("03_special_population/pregnancy_general.json", 0.95),
        ])

        ids = chunk_ids(selected)
        self.assertIn("POP_CHEMO_chunk_001", ids)
        self.assertIn("RED_IMMUNO_FEVER_chunk_001", ids)
        self.assertIn("SYM_FEVER_chunk_001", ids)
        self.assertNotIn("POP_PREGNANCY_GENERAL_chunk_001", ids)

    def test_mental_distress_ranks_mental_docs_above_headache_and_chest_noise(self):
        selected, _ = self.select("主诉: 心理困扰1周，逐渐加重，任务: 预问诊分诊", [
            load_doc("01_symptom_inquiry/headache.json", 0.92),
            load_doc("01_symptom_inquiry/chest_pain.json", 0.91),
            load_doc("01_symptom_inquiry/mental_distress.json", 0.82),
            load_doc("04_department_triage/mental_health_triage.json", 0.81),
            load_doc("05_medical_record_templates/mental_crisis_record_template.json", 0.80),
        ])

        ids = chunk_ids(selected)
        self.assertLess(ids.index("SYM_MENTAL_DISTRESS_chunk_001"), len(ids))
        self.assertIn("TRIAGE_MENTAL_chunk_001", ids)
        self.assertNotIn("SYM_HEADACHE_chunk_001", ids)
        self.assertNotIn("SYM_CHEST_PAIN_chunk_001", ids)

    def test_palpitation_not_displaced_by_mental_crisis_only_docs(self):
        selected, _ = self.select("主诉: 心悸半天，轻度伴随不适，任务: 预问诊分诊", [
            load_doc("04_department_triage/mental_health_triage.json", 0.95),
            load_doc("05_medical_record_templates/mental_crisis_record_template.json", 0.94),
            load_doc("02_red_flags/mental_crisis_red_flags.json", 0.93),
            load_doc("01_symptom_inquiry/palpitation.json", 0.82),
        ])

        ids = chunk_ids(selected)
        self.assertEqual(ids[0], "SYM_PALPITATION_chunk_001")
        self.assertNotIn("TRIAGE_MENTAL_chunk_001", ids)
        self.assertNotIn("MR_MENTAL_CRISIS_chunk_001", ids)

    def test_palpitation_with_chest_pain_or_syncope_keeps_red_flags_high(self):
        selected, _ = self.select("心悸伴胸痛和晕厥，任务: 预问诊分诊", [
            load_doc("01_symptom_inquiry/palpitation.json", 0.82),
            load_doc("02_red_flags/chest_pain_red_flags.json", 0.80),
            load_doc("01_symptom_inquiry/dizziness_syncope.json", 0.79),
            load_doc("02_red_flags/mental_crisis_red_flags.json", 0.95),
        ])

        ids = chunk_ids(selected)
        self.assertIn("RED_CHEST_PAIN_chunk_001", ids[:2])
        self.assertIn("SYM_PALPITATION_chunk_001", ids)
        self.assertNotIn("RED_MENTAL_CRISIS_chunk_001", ids)

    def test_mental_distress_with_suicidal_ideation_keeps_mental_crisis_red_flag_high(self):
        selected, _ = self.select("心理困扰，明确有自杀念头，不想活了，任务: 预问诊分诊", [
            load_doc("01_symptom_inquiry/mental_distress.json", 0.84),
            load_doc("02_red_flags/mental_crisis_red_flags.json", 0.82),
            load_doc("01_symptom_inquiry/headache.json", 0.95),
        ])

        self.assertIn("RED_MENTAL_CRISIS_chunk_001", chunk_ids(selected)[:2])

    def test_adult_cough_does_not_put_pediatric_only_doc_first(self):
        selected, _ = self.select("adult cough 咳嗽三天，有痰，任务: 预问诊分诊", [
            load_doc("04_department_triage/pediatric_triage.json", 0.95),
            load_doc("01_symptom_inquiry/cough.json", 0.82),
            load_doc("05_medical_record_templates/pediatric_fever_record_template.json", 0.94),
        ])

        ids = chunk_ids(selected)
        self.assertEqual(ids[0], "SYM_COUGH_chunk_001")
        self.assertNotIn("TRIAGE_PEDIATRIC_chunk_001", ids[:1])


if __name__ == "__main__":
    unittest.main()
