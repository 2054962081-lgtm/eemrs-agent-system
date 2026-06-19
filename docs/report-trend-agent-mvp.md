# Report Trend Agent MVP

## 1. Edge-cloud architecture

The report trend agent keeps encrypted report retrieval, local decryption, PII redaction, indicator structuring, abnormal detection, trend calculation, result encryption, and trace recording inside the local Spring Boot agent-server boundary.

The cloud model receives only allowlisted structured trend data:

- `analysis_task`
- `report_type`
- `date_range`
- `coarse_patient_context`
- `normalized_items`
- `trend_results`
- `abnormal_summary`
- `symptom_context_summary`
- `output_requirements`

The cloud model returns doctor-facing and patient-facing JSON summaries. It does not receive raw report text, ciphertext, keys, patient identifiers, doctor identifiers, visit identifiers, tokens, cookies, or API keys.

## 2. Why decryption is local

Report ciphertext is part of the system privacy boundary. Decryption is performed by local backend code before any model step. The MVP includes `LocalReportDecryptor` as the local decryption boundary and does not ask Ollama or the cloud model to perform cryptographic operations.

## 3. Why Ollama does not decrypt

Ollama is allowed only for local text assistance such as structuring, alias recognition, or compressed local summaries. This MVP uses deterministic Java parsing first. If Ollama is unavailable, report structuring still follows the rule parser.

## 4. Local PII redaction

`LocalPiiRedactor` removes direct identifier patterns such as ID card numbers, mobile numbers, and common patient fields before structuring text can be transformed into cloud payload data. Trace records only metadata, counts, hashes, and status.

## 5. Cloud payload allowlist

`CloudPayloadBuilder` constructs an allowlisted payload. `CloudPayloadPrivacyGuard` blocks forbidden keys and direct identifier patterns before cloud invocation. A violation returns `CLOUD_PAYLOAD_PRIVACY_VIOLATION` and does not call the cloud model.

## 6. Supported report type

The MVP supports `LAB` report trend analysis. OCR, imaging reports, diagnosis, prescriptions, and treatment plan generation are out of scope.

## 7. Supported indicator dictionary

The dictionary is stored in `agent-server/src/main/resources/lab/lab_indicator_dictionary.json` and currently includes WBC, NEUT_PERCENT, LYMPH_PERCENT, HGB, PLT, CRP, ALT, AST, TBIL, CREA, UREA, UA, GLU, HBA1C, TC, TG, LDL_C, and HDL_C.

## 8. Trend rules

For every normalized indicator the local service calculates latest value, previous value, min, max, absolute change, percent change, trend direction, latest abnormal flag, abnormal count, consecutive abnormal count, first abnormal date, and latest abnormal date.

The first version uses:

- Fewer than two points: `INSUFFICIENT_DATA`
- Three latest values continuously increasing: `INCREASING`
- Three latest values continuously decreasing: `DECREASING`
- Absolute percent change below `agent.report-trend.stable-change-threshold-percent`: `STABLE`
- Otherwise: recent two-point direction or `FLUCTUATING`

## 9. Cloud prompt

The cloud prompt requires JSON with `doctorSummary`, `patientExplanation`, `keyAbnormalItems`, `riskNotes`, `followUpQuestions`, `suggestedDepartment`, and `suggestedAction`. It forbids diagnosis, prescriptions, specific drugs, and replacing a doctor.

## 10. Trace steps

The MVP records:

`REPORT_ANALYSIS_REQUEST`, `REPORT_CIPHER_QUERY`, `LOCAL_DECRYPT`, `LOCAL_PII_REDACT`, `REPORT_STRUCTURING`, `INDICATOR_NORMALIZE`, `ABNORMAL_DETECTION`, `TREND_ANALYSIS`, `CLOUD_PAYLOAD_BUILD`, `CLOUD_MODEL_REQUEST`, `CLOUD_MODEL_RESPONSE`, `CLOUD_RESPONSE_VALIDATE`, `RESULT_ENCRYPT_STORE`, `FINAL_REPORT_SUMMARY`.

## 11. Error handling

Defined errors include `REPORT_NOT_FOUND`, `REPORT_DECRYPT_FAILED`, `REPORT_PARSE_FAILED`, `INDICATOR_NORMALIZE_FAILED`, `INSUFFICIENT_REPORT_DATA`, `CLOUD_PAYLOAD_PRIVACY_VIOLATION`, `CLOUD_MODEL_FAILED`, `CLOUD_RESPONSE_INVALID`, `RESULT_ENCRYPT_FAILED`, and `UNKNOWN_ERROR`.

Decryption failure and privacy guard failure stop before the cloud call.

## 12. Privacy boundary

The database stores encrypted analysis results in `lab_report_analysis_result.result_ciphertext` and a desensitized `result_summary`. It does not store full plaintext reports, full cloud payloads, full model replies, ciphertext keys, Authorization headers, cookies, or API keys.

## 13. Testing

Run:

```bash
cd agent-server
mvn -q test
cd ..
python -m unittest rag.test_rag_trace_meta
```

Automated tests use mock repositories and mock cloud model paths. They must not call real cloud APIs.

## 14. Current non-goals

OCR, imaging report analysis, automatic diagnosis, automatic prescriptions, automatic examination ordering, complex charts, and CI automatic evaluation are not implemented in this MVP.

## 15. Harness V2 follow-up

The initial cases live in `agent-server/src/test/resources/eval/report_trend_eval_cases.json`. The first report files live under `docs/eval/` and can be wired into the existing trace-based evaluation harness by mapping report trend responses into the current metric schema.
