from __future__ import annotations

import json
import math
import unittest

from rag.retrieval.fusion import RankedCandidate, fuse_rrf
from rag.retrieval.hybrid_retriever import HybridRetriever
from rag.retrieval.retrieval_service import RetrievalService
from rag.retrieval.sparse_retriever import SparseRetriever
from rag.schema.retrieval_models import RetrieveRequest


def entity(chunk_id: str, doc_type: str = "symptom_inquiry") -> dict:
    return {
        "chunk_id": chunk_id,
        "doc_id": f"DOC_{chunk_id}",
        "doc_type": doc_type,
        "title": f"title {chunk_id}",
        "related_symptoms": "胸痛；胸闷",
        "applicable_population": "成人",
        "related_departments": "心内科",
        "content_json": json.dumps({"must_ask": ["持续时间"]}, ensure_ascii=False),
        "chunk_text": f"text {chunk_id}",
    }


class FakeSparseStore:
    def __init__(self):
        self.calls = []

    def search_sparse(self, query, top_k, doc_type=None):
        self.calls.append({"query": query, "top_k": top_k, "doc_type": doc_type})
        return [
            {"distance": 8.2, "entity": entity("C")},
            {"distance": 7.1, "entity": entity("A")},
        ]


class FakeHybridStore:
    def __init__(self):
        self.dense_calls = []
        self.sparse_calls = []

    def search_dense(self, vector, top_k, doc_type=None):
        self.dense_calls.append({"vector": vector, "top_k": top_k, "doc_type": doc_type})
        return [
            {"distance": 0.9, "entity": entity("A", doc_type)},
            {"distance": 0.8, "entity": entity("B", doc_type)},
        ]

    def search_sparse(self, query, top_k, doc_type=None):
        self.sparse_calls.append({"query": query, "top_k": top_k, "doc_type": doc_type})
        return [
            {"distance": 8.2, "entity": entity("B", doc_type)},
            {"distance": 7.1, "entity": entity("C", doc_type)},
        ]


class FakeProvider:
    def __init__(self):
        self.encoded = []

    def encode_texts(self, texts):
        self.encoded.append(list(texts))
        return [[0.1, 0.2, 0.3]]


class HybridRetrievalTest(unittest.TestCase):
    def test_rrf_deduplicates_and_orders_deterministically(self):
        dense = [
            RankedCandidate("A", 1, 0.9, entity("A")),
            RankedCandidate("B", 2, 0.8, entity("B")),
            RankedCandidate("C", 3, 0.7, entity("C")),
        ]
        sparse = [
            RankedCandidate("C", 1, 8.0, entity("C")),
            RankedCandidate("A", 2, 7.0, entity("A")),
            RankedCandidate("D", 3, 6.0, entity("D")),
        ]

        fused = fuse_rrf(dense, sparse, rrf_k=60)

        self.assertEqual([item.chunk_id for item in fused], ["A", "C", "B", "D"])
        by_id = {item.chunk_id: item for item in fused}
        self.assertEqual(by_id["A"].dense_rank, 1)
        self.assertEqual(by_id["A"].sparse_rank, 2)
        self.assertEqual(by_id["D"].dense_rank, None)
        self.assertEqual(by_id["D"].sparse_rank, 3)
        self.assertTrue(math.isclose(by_id["A"].rrf_score, 1 / 61 + 1 / 62))

    def test_rrf_handles_empty_and_overlap(self):
        self.assertEqual(fuse_rrf([], []), [])
        only_dense = fuse_rrf([RankedCandidate("A", 1, 0.9, entity("A"))], [], rrf_k=60)
        self.assertEqual(only_dense[0].retrieval_channels, ("dense",))
        overlap = fuse_rrf(
            [RankedCandidate("A", 1, 0.9, entity("A"))],
            [RankedCandidate("A", 1, 9.0, entity("A"))],
            rrf_k=60,
        )
        self.assertEqual(overlap[0].retrieval_channels, ("dense", "bm25"))

    def test_sparse_retriever_maps_store_hits(self):
        store = FakeSparseStore()
        candidates = SparseRetriever(store).search("胸痛伴大汗", top_k=7, doc_type="red_flag")

        self.assertEqual(store.calls, [{"query": "胸痛伴大汗", "top_k": 7, "doc_type": "red_flag"}])
        self.assertEqual([(item.chunk_id, item.rank, item.score) for item in candidates], [("C", 1, 8.2), ("A", 2, 7.1)])

    def test_hybrid_retriever_runs_dense_sparse_then_fusion_per_doc_type(self):
        store = FakeHybridStore()
        result = HybridRetriever(store, rrf_k=60).search_doc_type([0.1, 0.2], "胸闷", top_k=5, doc_type="symptom_inquiry")

        self.assertEqual(store.dense_calls[0]["doc_type"], "symptom_inquiry")
        self.assertEqual(store.sparse_calls[0]["query"], "胸闷")
        self.assertEqual([item.chunk_id for item in result.fused_candidates], ["B", "A", "C"])
        self.assertEqual(result.trace["doc_type"], "symptom_inquiry")
        self.assertEqual(result.trace["fused_candidates"][0]["retrieval_channels"], ["dense", "bm25"])

    def test_retrieval_service_hybrid_preserves_public_dense_score(self):
        provider = FakeProvider()
        store = FakeHybridStore()
        request = RetrieveRequest(query="胸痛胸闷持续半小时，伴大汗", top_k=3, scene="pre_inquiry", include_doc_types=["symptom_inquiry"])

        response = RetrievalService(provider, store, retrieval_mode="hybrid_rrf", rrf_k=60).retrieve(request, {"trace_id": "t"})

        self.assertTrue(response.success)
        self.assertEqual(provider.encoded, [["胸痛胸闷持续半小时，伴大汗 急性冠脉综合征 放射痛 呼吸困难 急诊"]])
        self.assertEqual(response.trace_meta["hybrid_retrieval"]["dense_query"], provider.encoded[0][0])
        self.assertEqual(response.trace_meta["hybrid_retrieval"]["sparse_query"], request.query)
        self.assertEqual(response.chunks[0].chunk_id, "B")
        self.assertEqual(response.chunks[0].score, 0.8)
        self.assertGreater(response.chunks[0].final_score, 0.0)

    def test_retrieval_service_default_dense_mode_is_unchanged(self):
        provider = FakeProvider()
        store = FakeHybridStore()
        request = RetrieveRequest(query="胸痛胸闷持续半小时，伴大汗", top_k=3, scene="pre_inquiry", include_doc_types=["symptom_inquiry"])

        response = RetrievalService(provider, store).retrieve(request, {"trace_id": "t"})

        self.assertTrue(response.success)
        self.assertEqual(len(store.sparse_calls), 0)
        self.assertEqual(response.chunks[0].score, 0.9)
        self.assertNotIn("hybrid_retrieval", response.trace_meta)


if __name__ == "__main__":
    unittest.main()
