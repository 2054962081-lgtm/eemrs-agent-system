package com.liu.eemrsagent.eval;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ReportTrendTraceParser {
    private final TraceDetailParser parser;

    public ReportTrendTraceParser(ObjectMapper objectMapper) {
        this.parser = new TraceDetailParser(objectMapper);
    }

    public ReportTrendTraceEvidence parse(EvalTraceDetail detail) {
        ReportTrendTraceEvidence evidence = new ReportTrendTraceEvidence();
        evidence.setTraceRunId(string(detail.getRun(), "runId", "run_id"));
        evidence.setLatencyMs(longValue(detail.getRun(), "totalLatencyMs", "total_latency_ms"));
        evidence.setTotalTokens((int) longValue(detail.getRun(), "totalTokens", "total_tokens"));
        evidence.setModelName(string(detail.getRun(), "modelName", "model_name"));
        Set<Integer> sequences = new HashSet<>();
        for (EvalTraceStep step : detail.getSteps()) {
            evidence.getStepStatusMap().put(step.getStepType(), step.getStatus());
            if (step.getSequenceNo() != null && !sequences.add(step.getSequenceNo())) {
                evidence.setSequenceDuplicate(true);
            }
            if (step.getErrorCode() != null) {
                evidence.setErrorCode(step.getErrorCode());
            }
            Map<String, Object> output = parser.parseJsonObject(step.getResponsePayloadJson());
            Map<String, Object> metadata = parser.parseJsonObject(step.getMetadataJson());
            readStepEvidence(evidence, step, output, metadata);
        }
        return evidence;
    }

    public boolean isTraceComplete(EvalTraceDetail detail) {
        ReportTrendTraceEvidence evidence = parse(detail);
        if (detail.getRun().isEmpty() || evidence.getTraceRunId() == null || evidence.isSequenceDuplicate()) {
            return false;
        }
        for (String required : requiredSteps()) {
            if (!evidence.getStepStatusMap().containsKey(required)) {
                return false;
            }
        }
        return true;
    }

    public static Set<String> requiredSteps() {
        return Set.of(
                "REPORT_ANALYSIS_REQUEST", "REPORT_CIPHER_QUERY", "LOCAL_DECRYPT", "LOCAL_PII_REDACT",
                "REPORT_STRUCTURING", "INDICATOR_NORMALIZE", "ABNORMAL_DETECTION", "TREND_ANALYSIS",
                "CONTEXT_QUERY", "PRECONSULTATION_CONTEXT_LOAD", "LONG_TERM_HEALTH_CONTEXT_LOAD",
                "TRIAGE_CONTEXT_LOAD", "CONTEXT_REDACT", "CONTEXT_FUSION", "CLOUD_PAYLOAD_BUILD",
                "CLOUD_MODEL_REQUEST", "CLOUD_MODEL_RESPONSE", "CLOUD_RESPONSE_VALIDATE",
                "RESULT_ENCRYPT_STORE", "FINAL_REPORT_SUMMARY"
        );
    }

    private void readStepEvidence(ReportTrendTraceEvidence evidence, EvalTraceStep step,
                                  Map<String, Object> output, Map<String, Object> metadata) {
        String type = step.getStepType();
        if ("REPORT_ANALYSIS_REQUEST".equals(type)) {
            evidence.setAnalysisId(firstString(output, metadata, "analysis_id", "analysisId"));
        }
        if ("REPORT_CIPHER_QUERY".equals(type)) {
            evidence.setReportCount(intValue(output, metadata, "report_count", "reportCount"));
        }
        if ("REPORT_STRUCTURING".equals(type) || "INDICATOR_NORMALIZE".equals(type)) {
            evidence.setIndicatorCount(Math.max(evidence.getIndicatorCount(), intValue(output, metadata, "indicator_count", "indicatorCount")));
        }
        if ("ABNORMAL_DETECTION".equals(type)) {
            evidence.setAbnormalCount(intValue(output, metadata, "abnormal_count", "abnormalCount"));
        }
        if ("TREND_ANALYSIS".equals(type)) {
            evidence.setTrendItemCount(intValue(output, metadata, "trend_item_count", "trendItemCount"));
        }
        if ("PRECONSULTATION_CONTEXT_LOAD".equals(type) || "LONG_TERM_HEALTH_CONTEXT_LOAD".equals(type)) {
            evidence.setContextAvailable(evidence.isContextAvailable() || booleanValue(output, metadata, "context_available", "contextAvailable"));
            evidence.setSymptomTagCount(Math.max(evidence.getSymptomTagCount(), intValue(output, metadata, "symptom_tag_count", "symptomTagCount")));
            evidence.setChronicDiseaseTagCount(Math.max(evidence.getChronicDiseaseTagCount(), intValue(output, metadata, "chronic_disease_tag_count", "chronicDiseaseTagCount")));
        }
        if ("TRIAGE_CONTEXT_LOAD".equals(type) || "CONTEXT_QUERY".equals(type)) {
            String department = firstString(output, metadata, "recommended_department", "recommendedDepartment");
            if (department != null) {
                evidence.setRecommendedDepartment(department);
            }
        }
        if ("CONTEXT_FUSION".equals(type) || "CLOUD_PAYLOAD_BUILD".equals(type)) {
            evidence.setContextUsed(firstString(output, metadata, "context_used", "contextUsed"));
            evidence.setPayloadHash(firstString(output, metadata, "payload_hash", "payloadHash", "context_payload_hash"));
            evidence.setPrivacyGuardStatus("SUCCESS");
        }
        if ("CLOUD_MODEL_REQUEST".equals(type) || "CLOUD_MODEL_RESPONSE".equals(type)) {
            evidence.setResponseHash(firstString(output, metadata, "response_hash", "responseHash"));
            String model = firstString(output, metadata, "model_name", "modelName");
            if (model != null) {
                evidence.setModelName(model);
            }
        }
        if ("CLOUD_RESPONSE_VALIDATE".equals(type)) {
            evidence.setCloudResponseValid(!"FAILED".equals(step.getStatus()));
        }
    }

    private String firstString(Map<String, Object> first, Map<String, Object> second, String... keys) {
        String value = string(first, keys);
        return value == null ? string(second, keys) : value;
    }

    private String string(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    private int intValue(Map<String, Object> first, Map<String, Object> second, String... keys) {
        Object value = null;
        for (String key : keys) {
            value = first.containsKey(key) ? first.get(key) : second.get(key);
            if (value != null) break;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return 0;
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private long longValue(Map<String, Object> map, String... keys) {
        Object value = null;
        for (String key : keys) {
            if (map.containsKey(key)) {
                value = map.get(key);
                break;
            }
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null || String.valueOf(value).isBlank() ? 0L : Long.parseLong(String.valueOf(value));
    }

    private boolean booleanValue(Map<String, Object> first, Map<String, Object> second, String... keys) {
        Object value = null;
        for (String key : keys) {
            value = first.containsKey(key) ? first.get(key) : second.get(key);
            if (value != null) break;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }
}
