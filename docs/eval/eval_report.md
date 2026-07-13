# Agent Harness V2 Evaluation Report

- eval_time: 2026-06-19T00:00:00
- dataset_version: preconsultation-eval-v1
- case_file: agent-server/src/test/resources/eval/preconsultation_eval_cases.json
- case_count: 25
- model_name: mock-model
- prompt_version: preconsultation-deep-v1
- rag_version: mock-rag
- trace_schema_version: 1
- git_commit: sample

## Summary

- passed_cases: sample output
- failed_cases: sample output
- department_accuracy: deterministic rule metric
- primary_department_accuracy: deterministic rule metric
- must_ask_avg_coverage: deterministic rule metric
- red_flag_avg_hit_rate: deterministic rule metric
- follow_up_accuracy: deterministic rule metric
- tool_call_accuracy: deterministic rule metric
- trace_complete_rate: deterministic rule metric

## Top Bad Cases

- PC-TOOL-002 | 用户拒绝挂号 | NOT_OBSERVABLE_IN_CURRENT_ARCHITECTURE | Registration refusal is not observable in the current backend trace architecture.

Reports intentionally omit complete user medical text and keep only case id, scenario, run id, and metric summaries.
