import math
import unittest

from rag.eval.retrieval_metrics import compute_case_metric, compute_retrieval_metrics


def row(case_id, gold, retrieved):
    return {
        "case_id": case_id,
        "success": True,
        "expected_knowledge_topics": gold,
        "results": [{"doc_id": doc_id, "chunk_id": f"{doc_id}_CHUNK_001"} for doc_id in retrieved],
    }


class RetrievalMetricsTest(unittest.TestCase):
    def test_rank1_hit(self):
        metric = compute_case_metric(row("A", ["A"], ["A", "B", "C"]))

        self.assertEqual(metric.recall_at_5, 1.0)
        self.assertEqual(metric.reciprocal_rank, 1.0)
        self.assertEqual(metric.ndcg_at_5, 1.0)

    def test_rank2_hit(self):
        metric = compute_case_metric(row("B", ["A"], ["B", "A", "C"]))

        self.assertEqual(metric.recall_at_5, 1.0)
        self.assertEqual(metric.first_relevant_rank, 2)
        self.assertEqual(metric.reciprocal_rank, 0.5)

    def test_miss_contributes_zero(self):
        metric = compute_case_metric(row("C", ["A"], ["B", "C", "D"]))

        self.assertEqual(metric.recall_at_5, 0.0)
        self.assertIsNone(metric.first_relevant_rank)
        self.assertEqual(metric.reciprocal_rank, 0.0)

    def test_mrr_denominator_is_all_labeled_cases(self):
        metrics = compute_retrieval_metrics([
            row("rank1", ["A"], ["A"]),
            row("rank2", ["A"], ["B", "A"]),
            row("miss", ["A"], ["B", "C"]),
        ])

        self.assertEqual(metrics["MRR"], 0.5)

    def test_multi_gold_recall_counts_unique_gold(self):
        metric = compute_case_metric(row("E", ["A", "C"], ["A", "B", "C"]))

        self.assertEqual(metric.recall_at_5, 1.0)
        self.assertEqual(metric.retrieved_gold_count_at_5, 2)
        self.assertEqual(metric.recall_at_10, 1.0)

        early = compute_case_metric(row("E1", ["A", "C"], ["A", "B", "D"]))
        self.assertEqual(early.recall_at_5, 0.5)

    def test_binary_ndcg(self):
        metric = compute_case_metric(row("F", ["A", "C"], ["B", "A", "D", "C"]))

        expected_dcg = 1.0 / math.log2(2 + 1) + 1.0 / math.log2(4 + 1)
        expected_idcg = 1.0 / math.log2(1 + 1) + 1.0 / math.log2(2 + 1)
        self.assertAlmostEqual(metric.dcg_at_5, expected_dcg)
        self.assertAlmostEqual(metric.idcg_at_5, expected_idcg)
        self.assertAlmostEqual(metric.ndcg_at_5, expected_dcg / expected_idcg)


if __name__ == "__main__":
    unittest.main()
