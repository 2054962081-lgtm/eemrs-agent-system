# Medical RAG V1 Baseline

Medical RAG V1 is the current dense-only retrieval baseline used before Hybrid Retrieval, Rerank, Parent-Child Chunk, and related RAG V2 experiments.

Current retrieval path:

- Agent calls `POST /rag/retrieve`.
- The RAG service expands the medical query, embeds it with `BAAI/bge-small-zh-v1.5` by default, searches Milvus with COSINE, merges candidates by `chunk_id`, then applies the existing scene/doc_type selection logic.
- The baseline runner does not change the production request or response schema.

Run:

```bash
python -m rag.eval.run_baseline
```

Artifacts are written under:

```text
rag/eval/artifacts/medical_rag_v1_dense_<timestamp>_<run_id>/
```

Each run writes `baseline_manifest.json`, `retrieval_results.jsonl`, `retrieval_summary.json`, `agent_metrics_snapshot.json`, and `README.md`. The latest manifest is also copied to `rag/eval/baseline_manifest.json`.

Currently computable retrieval data includes request success rate, retrieval latency, returned chunk counts, doc_type distribution, chunk ranking traces, and document-topic metrics only when existing EEMRS Eval V1 `rag_ground_truth.expected_knowledge_topics` is present. No new retrieval gold labels are created.

Metrics requiring a dedicated reviewed Retrieval Gold Set should be treated as unavailable when that field is absent or insufficient for a case. This baseline is for later RAG V2 ablation and regression experiments; it does not represent the final version.
