# Pre-consultation Eval Case Schema

The canonical program-readable dataset is `preconsultation_eval_cases.json`.
The historical Excel files under `evaluation/data/` remain human-maintained review material.

Required fields per case:

- `case_id`: unique stable id.
- `source`: `existing_eval_set`, `added_harness_v2`, or `manual_badcase`.
- `scenario`: short scenario label, safe for reports.
- `category`: grouping label.
- `user_profile`: age group, gender, special population flags, and optional metadata.
- `turns`: user/assistant/system turns. Reports do not print the full text.
- `expected`: expected departments, must-ask keys, red flags, follow-up, tool-call expectation, risk level, and forbidden behaviors.
- `tags`: grouping tags for analysis.

`expectedToolCall = null` means the behavior is not observable in the current architecture and should be attributed as
`NOT_OBSERVABLE_IN_CURRENT_ARCHITECTURE` instead of forced pass/fail.
