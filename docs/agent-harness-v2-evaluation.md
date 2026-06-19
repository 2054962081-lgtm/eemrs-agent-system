# Agent Harness V2 Evaluation

Agent Harness V2 turns existing trace capture into repeatable evaluation and bad case attribution. It is a side-path capability only: it does not change pre-consultation business behavior, RAG retrieval, QuestionPlan generation, appointment flow, doctor-list APIs, frontend interaction, prompts, or medical knowledge content.

## Relationship To Harness V1

Harness V1 records `agent_run`, `agent_step`, and `tool_call` data. V2 consumes the trace detail shape:

- `run`
- `steps`
- `tool_calls`

V2 focuses on replaying standard cases, extracting observable trace evidence, calculating deterministic metrics, and attributing failures to the most likely pipeline stage.

## Dataset Integration

Historical assets remain in place:

- Human-maintained sample Excel: `evaluation/data/pre_consultation_eval_cases.sample.xlsx`
- Historical JSONL: `evaluation/data/pre_consultation_eval_cases.jsonl`
- Extended historical Excel/JSONL and reports under `evaluation/data/` and `evaluation/reports/`
- Bad case review documents in the project root

The program-readable Harness V2 dataset is:

- `agent-server/src/test/resources/eval/preconsultation_eval_cases.json`
- `agent-server/src/test/resources/eval/preconsultation_eval_cases_extra.json`
- `agent-server/src/test/resources/eval/preconsultation_eval_schema.md`

The standard dataset currently contains 25 cases: 5 migrated from the existing eval set with `source = existing_eval_set`, and 20 added Harness V2 cases with `source = added_harness_v2`.

## Case Schema

Each case contains:

- `case_id`: unique stable id.
- `source`: `existing_eval_set`, `added_harness_v2`, or `manual_badcase`.
- `scenario`: short safe label for reports.
- `category`: red flag, common triage, special population, insufficient info, or tool-call group.
- `user_profile`: age group, gender, and special-population metadata.
- `turns`: single-turn or multi-turn conversation input. Full text is not printed in reports.
- `expected`: expected departments, primary department, must-ask keys, red flags, follow-up expectation, registration/tool expectation, risk level, and forbidden behavior notes.
- `tags`: grouping tags.

`expectedToolCall = null` means the event is not observable in the current architecture. The attributor reports `NOT_OBSERVABLE_IN_CURRENT_ARCHITECTURE` instead of forcing a pass/fail judgment.

## Runner Usage

The focused eval tests can be run with:

```bash
cd agent-server
mvn -q "-Dtest=EvalCaseLoaderTest,TraceBasedEvaluatorTest,BadCaseAttributorTest,EvalReportWriterTest,PreConsultationEvalRunnerTest" test
```

`PreConsultationTraceEvalRunner.mockRunner()` reads the dataset, creates a deterministic mock run id per case, builds mock trace detail, evaluates metrics, attributes bad cases, and writes reports. It does not call a real cloud model, real RAG, patient data, Docker, or external APIs.

The runner config supports:

- `caseFile`
- `outputDir`
- `maxCases`, where `0` means all cases
- `failFast`
- metadata: dataset version, model name, prompt version, RAG version, trace schema version, git commit

## Metrics

V2 implements deterministic rule metrics:

- `department_correct`: actual department is in expected departments. Empty expected departments are excluded.
- `primary_department_correct`: actual department equals primary department. Empty primary department is excluded.
- `must_ask_coverage`: matched expected must-ask keys divided by expected must-ask count.
- `red_flag_hit_rate`: matched red flag keys divided by expected red flag count.
- `follow_up_correct`: trace follow-up decision equals expected `shouldFollowUp`.
- `tool_call_correct`: observed tool call equals expected tool call, or `NOT_OBSERVABLE_IN_CURRENT_ARCHITECTURE`.
- `format_stability`: records parse/fallback/missing-field evidence from `POST_PROCESS`.
- `trace_complete`: run exists, run id exists, key steps are present, and sequence numbers are not duplicated.

Matching is key/description containment only. V2 intentionally does not use LLM judging or semantic similarity.

## Bad Case Attribution

Implemented failure stages:

- `NONE`
- `RETRIEVAL_ERROR`
- `QUESTION_PLAN_ERROR`
- `MODEL_REQUEST_ERROR`
- `MODEL_OUTPUT_ERROR`
- `POST_PROCESS_ERROR`
- `FOLLOW_UP_DECISION_ERROR`
- `TOOL_DECISION_ERROR`
- `TOOL_EXECUTION_ERROR`
- `TRACE_INCOMPLETE`
- `NOT_OBSERVABLE_IN_CURRENT_ARCHITECTURE`
- `UNKNOWN`

Rules are trace-first:

- Missing run, missing key steps, or duplicated sequence: `TRACE_INCOMPLETE`
- Expected red flags but RAG lacks red flag/chunk/doc metadata: `RETRIEVAL_ERROR`
- RAG recalls relevant material but QuestionPlan misses expected must-ask items: `QUESTION_PLAN_ERROR`
- QuestionPlan contains must-ask items but model/final answer does not: `MODEL_OUTPUT_ERROR`
- `POST_PROCESS` reports parse failure, fallback parsing, missing fields, or empty department: `POST_PROCESS_ERROR`
- Follow-up decision differs from expected: `FOLLOW_UP_DECISION_ERROR`
- Tool call occurrence differs from expected confirmation state: `TOOL_DECISION_ERROR`
- Tool call status is failed or timeout: `TOOL_EXECUTION_ERROR`
- Registration refusal is not observable in the current backend trace shape: `NOT_OBSERVABLE_IN_CURRENT_ARCHITECTURE`

## Outputs

Each run writes:

- `eval_results.csv`
- `badcase_analysis.json`
- `eval_report.md`

Reports include `case_id`, scenario, run id, metrics, and stage summaries. They intentionally omit full patient text.

## Privacy

The dataset is synthetic or historical eval material only. Reports avoid complete medical source text and should be treated as metadata summaries for engineering diagnosis.

## Current Non-goals

- LLM-as-judge scoring
- Semantic similarity matching
- Trace Grading
- CI automatic evaluation
- Frontend evaluation page
- OpenTelemetry
- Automatic alerts
- Longitudinal report analysis
- Business flow changes

## Future Extensions

V2 can later feed CI by running the mock runner on every PR and an integration profile on scheduled builds. Trace Grading can be layered on top of the same parsed trace detail. RAG bad case optimization can consume `RETRIEVAL_ERROR` and `QUESTION_PLAN_ERROR` distributions without changing the user-facing pre-consultation path.
