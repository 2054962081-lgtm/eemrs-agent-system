from __future__ import annotations

import json
import unittest
from unittest.mock import patch

from rag.retrieval.fusion import FusedCandidate
from rag.retrieval.reranker import CrossEncoderReranker
from rag.retrieval.retrieval_service import RetrievalService
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


def fused(chunk_id: str, rrf_score: float, dense_rank: int | None = None, sparse_rank: int | None = None) -> FusedCandidate:
    channels = []
    if dense_rank is not None:
        channels.append("dense")
    if sparse_rank is not None:
        channels.append("bm25")
    return FusedCandidate(
        chunk_id=chunk_id,
        entity=entity(chunk_id),
        rrf_score=rrf_score,
        dense_rank=dense_rank,
        dense_score=0.9 - (dense_rank or 9) / 100 if dense_rank is not None else None,
        sparse_rank=sparse_rank,
        sparse_score=9.0 - (sparse_rank or 9) if sparse_rank is not None else None,
        retrieval_channels=tuple(channels),
    )


class FakeScorer:
    def __init__(self, scores):
        self.scores = scores
        self.calls = []

    def predict(self, pairs, batch_size):
        self.calls.append({"pairs": pairs, "batch_size": batch_size})
        return list(self.scores)


class FakeProvider:
    def __init__(self):
        self.encoded = []

    def encode_texts(self, texts):
        self.encoded.append(list(texts))
        return [[0.1, 0.2, 0.3]]


class FakeStore:
    def __init__(self):
        self.calls = []

    def search_dense(self, vector, top_k, doc_type=None):
        self.calls.append("dense")
        return [
            {"distance": 0.9, "entity": entity("A", doc_type)},
            {"distance": 0.8, "entity": entity("B", doc_type)},
        ]

    def search_sparse(self, query, top_k, doc_type=None):
        self.calls.append("bm25")
        return [
            {"distance": 8.0, "entity": entity("B", doc_type)},
            {"distance": 7.0, "entity": entity("C", doc_type)},
        ]


class RerankerTest(unittest.TestCase):
    def test_reranker_mapping_and_metadata_are_preserved(self):
        scorer = FakeScorer([0.2, 0.9, 0.5])
        candidates = [fused("A", 0.03, 1), fused("B", 0.02, 2), fused("C", 0.01, None, 1)]

        result = CrossEncoderReranker(scorer, batch_size=8).rerank("胸痛", candidates)

        self.assertEqual([item.chunk_id for item in result], ["B", "C", "A"])
        self.assertEqual(result[0].entity["doc_id"], "DOC_B")
        self.assertEqual(result[0].entity["_rerank_score"], 0.9)
        self.assertEqual(result[0].entity["_rerank_rank"], 1)
        self.assertEqual(result[2].entity["_rrf_rank"], 1)

    def test_stable_tie_uses_original_rrf_rank(self):
        scorer = FakeScorer([0.5, 0.5, 0.5])
        candidates = [fused("A", 0.03), fused("B", 0.02), fused("C", 0.01)]

        result = CrossEncoderReranker(scorer).rerank("胸痛", candidates)

        self.assertEqual([item.chunk_id for item in result], ["A", "B", "C"])

    def test_empty_and_one_candidate(self):
        scorer = FakeScorer([])
        self.assertEqual(CrossEncoderReranker(scorer).rerank("胸痛", []), [])

        one = CrossEncoderReranker(FakeScorer([0.7])).rerank("胸痛", [fused("A", 0.03)])
        self.assertEqual(one[0].chunk_id, "A")
        self.assertEqual(one[0].entity["_rerank_rank"], 1)

    def test_batch_mapping_keeps_pair_order(self):
        scorer = FakeScorer([0.1, 0.2])
        candidates = [fused("A", 0.03), fused("B", 0.02)]

        CrossEncoderReranker(scorer, batch_size=4).rerank("胸痛", candidates)

        self.assertEqual(scorer.calls[0]["batch_size"], 4)
        self.assertEqual(scorer.calls[0]["pairs"], [["胸痛", "text A"], ["胸痛", "text B"]])

    def test_pipeline_calls_dense_bm25_rerank_then_selection(self):
        provider = FakeProvider()
        store = FakeStore()
        request = RetrieveRequest(query="胸痛胸闷持续半小时，伴大汗", top_k=3, scene="pre_inquiry", include_doc_types=["symptom_inquiry"])
        reranker = CrossEncoderReranker(FakeScorer([0.9, 0.2, 0.5]))

        with patch("rag.retrieval.retrieval_service.get_default_reranker", return_value=reranker):
            response = RetrievalService(provider, store, retrieval_mode="hybrid_rerank").retrieve(request, {"trace_id": "t"})

        self.assertTrue(response.success)
        self.assertEqual(store.calls, ["dense", "bm25"])
        self.assertEqual(response.chunks[0].chunk_id, "B")
        self.assertEqual(response.chunks[0].score, 0.8)
        trace = response.trace_meta["hybrid_retrieval"]
        self.assertEqual(trace["retrieval_mode"], "hybrid_rerank")
        self.assertEqual(trace["reranker_query"], request.query)
        self.assertIn("rerank_candidates", trace["doc_type_traces"][0])
        self.assertEqual(trace["selected_candidates"][0]["rerank_rank"], 1)

    def test_existing_modes_do_not_use_reranker(self):
        for mode in ["dense", "bm25", "hybrid_rrf"]:
            with self.subTest(mode=mode):
                provider = FakeProvider()
                store = FakeStore()
                request = RetrieveRequest(query="胸痛胸闷持续半小时，伴大汗", top_k=3, scene="pre_inquiry", include_doc_types=["symptom_inquiry"])
                with patch("rag.retrieval.retrieval_service.get_default_reranker") as mocked:
                    response = RetrievalService(provider, store, retrieval_mode=mode).retrieve(request, {"trace_id": "t"})
                self.assertTrue(response.success)
                mocked.assert_not_called()

    def test_trace_shape_by_mode(self):
        request = RetrieveRequest(query="胸痛胸闷持续半小时，伴大汗", top_k=3, scene="pre_inquiry", include_doc_types=["symptom_inquiry"])

        dense = RetrievalService(FakeProvider(), FakeStore(), retrieval_mode="dense").retrieve(request, {"trace_id": "t"})
        self.assertNotIn("hybrid_retrieval", dense.trace_meta)
        self.assertNotIn("bm25_retrieval", dense.trace_meta)
        self.assertIn("retrieval_selection", dense.trace_meta)

        bm25 = RetrievalService(FakeProvider(), FakeStore(), retrieval_mode="bm25").retrieve(request, {"trace_id": "t"})
        self.assertIn("bm25_retrieval", bm25.trace_meta)
        self.assertEqual(bm25.trace_meta["bm25_retrieval"]["dense_query"], None)
        self.assertIn("sparse_candidates", bm25.trace_meta["bm25_retrieval"]["doc_type_traces"][0])

        rrf = RetrievalService(FakeProvider(), FakeStore(), retrieval_mode="hybrid_rrf").retrieve(request, {"trace_id": "t"})
        self.assertIn("hybrid_retrieval", rrf.trace_meta)
        self.assertEqual(rrf.trace_meta["hybrid_retrieval"]["retrieval_mode"], "hybrid_rrf")
        self.assertIn("dense_candidates", rrf.trace_meta["hybrid_retrieval"]["doc_type_traces"][0])
        self.assertIn("sparse_candidates", rrf.trace_meta["hybrid_retrieval"]["doc_type_traces"][0])
        self.assertIn("fused_candidates", rrf.trace_meta["hybrid_retrieval"]["doc_type_traces"][0])
        self.assertNotIn("rerank_candidates", rrf.trace_meta["hybrid_retrieval"]["doc_type_traces"][0])

    def test_trace_meta_does_not_affect_retrieval_selection(self):
        request = RetrieveRequest(query="胸痛胸闷持续半小时，伴大汗", top_k=3, scene="pre_inquiry", include_doc_types=["symptom_inquiry"])

        left = RetrievalService(FakeProvider(), FakeStore(), retrieval_mode="hybrid_rrf").retrieve(request, {"trace_id": "left"})
        right = RetrievalService(FakeProvider(), FakeStore(), retrieval_mode="hybrid_rrf").retrieve(request, {"trace_id": "right", "debug": True})

        self.assertEqual([chunk.chunk_id for chunk in left.chunks], [chunk.chunk_id for chunk in right.chunks])
        self.assertEqual([chunk.final_score for chunk in left.chunks], [chunk.final_score for chunk in right.chunks])


if __name__ == "__main__":
    unittest.main()
