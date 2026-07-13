package com.liu.eemrsagent.eval;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TraceBasedEvaluator {
    private final TraceDetailParser parser;

    public TraceBasedEvaluator(ObjectMapper objectMapper) {
        this.parser = new TraceDetailParser(objectMapper);
    }

    public EvalActualResult extractActual(EvalTraceDetail detail) {
        EvalActualResult actual = new EvalActualResult();
        actual.setRunId(string(detail.getRun(), "runId", "run_id"));
        actual.setLatencyMs(longNumber(detail.getRun(), "totalLatencyMs", "total_latency_ms"));
        actual.setTotalTokens((int) longNumber(detail.getRun(), "totalTokens", "total_tokens"));
        actual.setTraceComplete(isTraceComplete(detail));
        actual.setToolCall(!detail.getToolCalls().isEmpty() || detail.hasStep("TOOL_CALL"));
        extractPostProcess(detail, actual);
        extractFollowUp(detail, actual);
        return actual;
    }

    public EvalResult evaluate(EvalCase evalCase, EvalTraceDetail detail) {
        EvalActualResult actual = extractActual(detail);
        EvalExpectedResult expected = evalCase.getExpected();
        List<String> expectedDepartments = expected.getExpectedDepartments();
        actual.setMatchedMustAsk(matchKeys(expected.getExpectedMustAsk(), detail.joinedText(
                "QUESTION_PLAN", "MODEL_RESPONSE", "FINAL_ANSWER", "POST_PROCESS")));
        actual.setMatchedRedFlags(matchKeys(expected.getExpectedRedFlags(), detail.joinedText(
                "RAG_RETRIEVAL", "QUESTION_PLAN", "POST_PROCESS", "FINAL_ANSWER")));

        EvalResult result = new EvalResult();
        result.setCaseId(evalCase.getCaseId());
        result.setSource(evalCase.getSource());
        result.setScenario(evalCase.getScenario());
        result.setCategory(evalCase.getCategory());
        result.setRiskLevel(expected.getRiskLevel());
        result.setRunId(actual.getRunId());
        result.setActualDepartment(actual.getActualDepartment());
        result.setExpectedDepartments(String.join("|", expectedDepartments));
        result.setDepartmentCorrect(expectedDepartments.isEmpty()
                ? null
                : expectedDepartments.contains(actual.getActualDepartment()));
        result.setPrimaryDepartmentCorrect(expected.getPrimaryDepartment() == null || expected.getPrimaryDepartment().isBlank()
                ? null
                : expected.getPrimaryDepartment().equals(actual.getActualDepartment()));
        result.setMustAskCoverage(ratio(actual.getMatchedMustAsk().size(), expected.getExpectedMustAsk().size()));
        result.setRedFlagHitRate(ratio(actual.getMatchedRedFlags().size(), expected.getExpectedRedFlags().size()));
        result.setFollowUpExpected(expected.isShouldFollowUp());
        result.setFollowUpActual(actual.isNeedFollowUp());
        result.setFollowUpCorrect(expected.isShouldFollowUp() == actual.isNeedFollowUp());
        result.setToolCallExpected(expected.getExpectedToolCall() == null ? "NOT_OBSERVABLE_IN_CURRENT_ARCHITECTURE" : String.valueOf(expected.getExpectedToolCall()));
        result.setToolCallActual(String.valueOf(actual.isToolCall()));
        result.setToolCallCorrect(expected.getExpectedToolCall() == null
                ? "NOT_OBSERVABLE_IN_CURRENT_ARCHITECTURE"
                : String.valueOf(expected.getExpectedToolCall() == actual.isToolCall()));
        result.setLatencyMs(actual.getLatencyMs());
        result.setTotalTokens(actual.getTotalTokens());
        result.setTraceComplete(actual.isTraceComplete());
        return result;
    }

    public boolean isTraceComplete(EvalTraceDetail detail) {
        if (detail.getRun().isEmpty() || string(detail.getRun(), "runId", "run_id") == null) {
            return false;
        }
        if (!detail.hasStep("USER_INPUT") || !detail.hasStep("FINAL_ANSWER")) {
            return false;
        }
        Set<Integer> seen = new HashSet<>();
        for (EvalTraceStep step : detail.getSteps()) {
            Integer sequenceNo = step.getSequenceNo();
            if (sequenceNo != null && !seen.add(sequenceNo)) {
                return false;
            }
        }
        return true;
    }

    private void extractPostProcess(EvalTraceDetail detail, EvalActualResult actual) {
        detail.firstStep("POST_PROCESS").ifPresent(step -> {
            Map<String, Object> payload = parser.parseJsonObject(step.getResponsePayloadJson());
            Map<String, Object> metadata = parser.parseJsonObject(step.getMetadataJson());
            String department = firstString(payload, metadata, "actual_department", "department", "recommendedDepartment", "primaryDepartment");
            if (department == null) {
                department = step.getOutputSummary();
            }
            actual.setActualDepartment(department);
            actual.setJsonParseFailed(booleanValue(payload, metadata, "json_parse_failed", "jsonParseFailed"));
            actual.setFallbackParseUsed(booleanValue(payload, metadata, "fallback_parse_used", "fallbackParseUsed"));
            actual.setMissingFields(booleanValue(payload, metadata, "missing_fields", "missingFields"));
        });
    }

    private void extractFollowUp(EvalTraceDetail detail, EvalActualResult actual) {
        detail.firstStep("FOLLOW_UP_DECISION").ifPresent(step -> {
            Map<String, Object> payload = parser.parseJsonObject(step.getResponsePayloadJson());
            Map<String, Object> metadata = parser.parseJsonObject(step.getMetadataJson());
            actual.setNeedFollowUp(booleanValue(payload, metadata, "need_follow_up", "needFollowUp", "should_follow_up"));
        });
    }

    private List<String> matchKeys(List<EvalKeyPoint> expected, String text) {
        String normalized = text == null ? "" : text.toLowerCase();
        return expected.stream()
                .filter(point -> contains(normalized, point.getKey()) || contains(normalized, point.getDescription()))
                .map(EvalKeyPoint::getKey)
                .toList();
    }

    private boolean contains(String normalized, String token) {
        return token != null && !token.isBlank() && normalized.contains(token.toLowerCase());
    }

    private double ratio(int matched, int total) {
        if (total == 0) {
            return 1.0d;
        }
        return Math.round((matched * 1.0d / total) * 10000.0d) / 10000.0d;
    }

    private String firstString(Map<String, Object> first, Map<String, Object> second, String... keys) {
        for (String key : keys) {
            Object value = first.containsKey(key) ? first.get(key) : second.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    private boolean booleanValue(Map<String, Object> first, Map<String, Object> second, String... keys) {
        for (String key : keys) {
            Object value = first.containsKey(key) ? first.get(key) : second.get(key);
            if (value instanceof Boolean bool) {
                return bool;
            }
            if (value != null && !String.valueOf(value).isBlank()) {
                return Boolean.parseBoolean(String.valueOf(value));
            }
        }
        return false;
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

    private long longNumber(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value instanceof Number number) {
                return number.longValue();
            }
            if (value != null && !String.valueOf(value).isBlank()) {
                return Long.parseLong(String.valueOf(value));
            }
        }
        return 0L;
    }
}
