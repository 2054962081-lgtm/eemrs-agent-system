from __future__ import annotations

import json
import unittest

from fastapi.routing import APIRoute

from rag import rag_api_server
from rag.retrieval.query_expansion import expand_medical_query
from rag.retrieval.retrieval_service import RetrievalService
from rag.retrieval.scene_policy import doc_type_limits, ordered_doc_types, quota_for_scene
from rag.schema.retrieval_models import RetrieveRequest, RetrieveResponse


class FakeProvider:
    def __init__(self):
        self.texts = []

    def encode_texts(self, texts):
        self.texts.append(list(texts))
        return [[0.1, 0.2, 0.3]]


class FakeStore:
    def __init__(self):
        self.calls = []

    def search_dense(self, vector, top_k, doc_type=None):
        self.calls.append({"vector": vector, "top_k": top_k, "doc_type": doc_type})
        if doc_type != "symptom_inquiry":
            return []
        entity = {
            "chunk_id": "SYM_CHEST_PAIN_CHUNK_001",
            "doc_id": "SYM_CHEST_PAIN_001",
            "doc_type": "symptom_inquiry",
            "title": "胸痛/胸闷问诊模板",
            "urgency_level": "",
            "related_departments": "心内科；急诊科",
            "applicable_population": "成人",
            "related_symptoms": "胸痛；胸闷",
            "content_json": json.dumps({"must_ask": ["持续时间"], "red_flags": []}, ensure_ascii=False),
            "chunk_text": "胸痛问诊模板",
        }
        return [{"distance": 0.91, "entity": entity}]


class RagRefactorContractTest(unittest.TestCase):
    def test_query_expansion_examples_are_stable(self):
        self.assertEqual(
            expand_medical_query("我爸72岁胸口闷还冒汗", "pre_inquiry"),
            "我爸72岁胸口闷还冒汗 老年人 症状不典型 心梗 卒中 感染 跌倒骨折 意识改变",
        )
        self.assertEqual(
            expand_medical_query("怀孕32周头痛眼花腿肿", "deep_inquiry"),
            "怀孕32周头痛眼花腿肿 孕产妇 孕周 胎动 阴道出血流液 血压 水肿 子痫前期 病程 诱因 伴随症状 既往史 用药史 过敏史 红旗信号",
        )
        self.assertEqual(
            expand_medical_query("心理困扰，不想活了", "pre_inquiry"),
            "心理困扰，不想活了 自伤自杀风险 立即陪伴 急诊 120 危机干预",
        )

    def test_scene_policy_values_are_stable(self):
        self.assertEqual(list(quota_for_scene("pre_inquiry").items()), [
            ("red_flag", 2),
            ("symptom_inquiry", 2),
            ("special_population", 2),
            ("department_triage", 1),
            ("medical_record_template", 1),
        ])
        self.assertEqual(list(quota_for_scene("medical_record").items()), [
            ("medical_record_template", 3),
            ("symptom_inquiry", 2),
            ("special_population", 2),
            ("red_flag", 1),
            ("department_triage", 1),
        ])
        self.assertEqual(
            ordered_doc_types("medical_record", None),
            ["medical_record_template", "symptom_inquiry", "special_population", "red_flag", "department_triage"],
        )
        self.assertEqual(list(doc_type_limits("pre_inquiry", ["unknown", "red_flag"], 8).items()), [("red_flag", 2)])

    def test_retrieval_service_preserves_orchestration_parameters(self):
        provider = FakeProvider()
        store = FakeStore()
        request = RetrieveRequest(query="胸痛胸闷持续半小时，伴大汗", top_k=8, scene="pre_inquiry")

        response = RetrievalService(provider, store).retrieve(request, {"trace_id": "trace-1"})

        self.assertTrue(response.success)
        self.assertEqual(provider.texts, [["胸痛胸闷持续半小时，伴大汗 急性冠脉综合征 放射痛 呼吸困难 急诊"]])
        self.assertEqual([call["doc_type"] for call in store.calls], [
            "red_flag",
            "symptom_inquiry",
            "special_population",
            "department_triage",
            "medical_record_template",
        ])
        self.assertEqual([call["top_k"] for call in store.calls], [24, 24, 24, 24, 24])
        self.assertEqual([chunk.chunk_id for chunk in response.chunks], ["SYM_CHEST_PAIN_CHUNK_001"])
        self.assertEqual(response.trace_meta["trace_id"], "trace-1")

    def test_api_contract_and_endpoint_inventory_are_stable(self):
        self.assertEqual(list(RetrieveRequest.model_fields), ["query", "top_k", "include_doc_types", "scene"])
        self.assertEqual(RetrieveRequest.model_fields["top_k"].default, 5)
        self.assertEqual(RetrieveRequest.model_fields["scene"].default, "pre_inquiry")
        self.assertEqual(list(RetrieveResponse.model_fields), [
            "success",
            "query",
            "expanded_query",
            "doc_type_counts",
            "used_query_expansion",
            "chunks",
            "trace_meta",
            "error_message",
        ])
        inventory = {
            (next(iter(route.methods - {"HEAD"})), route.path): route.response_model
            for route in rag_api_server.app.routes
            if isinstance(route, APIRoute)
        }
        self.assertEqual(str(inventory[("GET", "/health")]), "dict[str, typing.Any]")
        self.assertEqual(inventory[("POST", "/rag/retrieve")], RetrieveResponse)
        self.assertEqual(inventory[("POST", "/memory/upsert")].__name__, "MemoryUpsertResponse")
        self.assertEqual(inventory[("POST", "/memory/search")].__name__, "MemorySearchResponse")
        self.assertEqual(inventory[("POST", "/memory/delete-by-source")].__name__, "MemoryDeleteResponse")
        self.assertEqual(inventory[("GET", "/memory/health")].__name__, "MemoryHealthResponse")

    def test_health_and_memory_endpoint_smoke_without_clients(self):
        rag_api_server.milvus = None
        rag_api_server.user_memory_milvus = None
        self.assertEqual(rag_api_server.health()["success"], False)
        memory_health = rag_api_server.memory_health()
        self.assertIsInstance(memory_health.success, bool)
        self.assertIsInstance(memory_health.collection_exists, bool)
        self.assertEqual(memory_health.collection, "medical_user_memory")


if __name__ == "__main__":
    unittest.main()
