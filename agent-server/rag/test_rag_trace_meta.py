import unittest

from rag.rag_api_server import RetrieveResponse, build_trace_meta


class RagTraceMetaTest(unittest.TestCase):

    def test_build_trace_meta_omits_missing_headers(self):
        trace_meta = build_trace_meta("trace-1", "run-1", None, "session-1")

        self.assertEqual(trace_meta["trace_id"], "trace-1")
        self.assertEqual(trace_meta["run_id"], "run-1")
        self.assertEqual(trace_meta["session_id"], "session-1")
        self.assertEqual(trace_meta["service"], "medical-rag")
        self.assertEqual(trace_meta["rag_version"], "medical-rag-v1")
        self.assertNotIn("step_id", trace_meta)

    def test_retrieve_response_keeps_trace_meta_optional(self):
        response = RetrieveResponse(success=True, query="q")

        self.assertTrue(response.success)
        self.assertEqual(response.trace_meta, {})
        self.assertEqual(response.chunks, [])


if __name__ == "__main__":
    unittest.main()
