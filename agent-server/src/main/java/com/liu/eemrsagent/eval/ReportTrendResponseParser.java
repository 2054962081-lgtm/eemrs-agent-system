package com.liu.eemrsagent.eval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ReportTrendResponseParser {
    private final ObjectMapper objectMapper;

    public ReportTrendResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @SuppressWarnings("unchecked")
    public ReportTrendActualResult parse(Object response) {
        Map<String, Object> map = objectMapper.convertValue(response, LinkedHashMap.class);
        ReportTrendActualResult actual = new ReportTrendActualResult();
        actual.setAnalysisId(string(map, "analysisId", "analysis_id"));
        actual.setTraceRunId(string(map, "traceRunId", "trace_run_id"));
        actual.setStatus(string(map, "status"));
        actual.setDoctorSummary(string(map, "doctorSummary", "doctor_summary"));
        actual.setPatientExplanation(string(map, "patientExplanation", "patient_explanation"));
        actual.setContextualInterpretation(string(map, "contextualInterpretation", "contextual_interpretation"));
        actual.setContextLinks(listOfMaps(map.get("contextLinks")));
        actual.setAbnormalItems(listOfMaps(map.get("abnormalItems")));
        actual.setTrendItems(listOfMaps(map.get("trendItems")));
        actual.setFollowUpQuestions(listOfString(map.get("followUpQuestions")));
        Map<String, Object> recommendation = map.get("recommendation") instanceof Map<?, ?> rec ? objectMapper.convertValue(rec, LinkedHashMap.class) : Map.of();
        actual.setSuggestedDepartment(firstString(recommendation, map, "suggestedDepartment", "suggested_department"));
        actual.setSuggestedAction(firstString(recommendation, map, "suggestedAction", "suggested_action"));
        actual.setErrorCode(string(map, "errorCode", "error_code"));
        actual.setSanitizedText(sanitizedText(actual));
        return actual;
    }

    public ReportTrendActualResult parseJson(String json) {
        try {
            return parse(objectMapper.readValue(json, LinkedHashMap.class));
        } catch (JsonProcessingException e) {
            ReportTrendActualResult actual = new ReportTrendActualResult();
            actual.setStatus("FAILED");
            actual.setErrorCode("CLOUD_RESPONSE_INVALID");
            return actual;
        }
    }

    private String sanitizedText(ReportTrendActualResult actual) {
        String text = String.join(" ",
                safe(actual.getDoctorSummary()),
                safe(actual.getPatientExplanation()),
                safe(actual.getContextualInterpretation()),
                String.valueOf(actual.getContextLinks()),
                String.valueOf(actual.getAbnormalItems()),
                String.valueOf(actual.getTrendItems()),
                safe(actual.getSuggestedDepartment()),
                safe(actual.getSuggestedAction()));
        return text.replaceAll("\\b\\d{17}[0-9Xx]\\b", "[REDACTED_ID_CARD]")
                .replaceAll("(?<!\\d)1[3-9]\\d{9}(?!\\d)", "[REDACTED_MOBILE]");
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listOfMaps(Object value) {
        if (!(value instanceof List<?> list)) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?>) {
                out.add(objectMapper.convertValue(item, LinkedHashMap.class));
            }
        }
        return out;
    }

    private List<String> listOfString(Object value) {
        if (!(value instanceof List<?> list)) {
            return new ArrayList<>();
        }
        return list.stream().map(String::valueOf).toList();
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
}
