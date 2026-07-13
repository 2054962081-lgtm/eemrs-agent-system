package com.liu.eemrsagent.eval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TraceDetailParser {
    private final ObjectMapper objectMapper;

    public TraceDetailParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @SuppressWarnings("unchecked")
    public EvalTraceDetail parse(Object rawDetail) {
        Map<String, Object> detail = objectMapper.convertValue(rawDetail, Map.class);
        EvalTraceDetail parsed = new EvalTraceDetail();
        parsed.setRun(asMap(detail.get("run")));
        parsed.setSteps(parseSteps(asList(detail.get("steps"))));
        Object toolCalls = detail.containsKey("tool_calls") ? detail.get("tool_calls") : detail.get("toolCalls");
        parsed.setToolCalls(parseToolCalls(asList(toolCalls)));
        return parsed;
    }

    public Map<String, Object> parseJsonObject(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory()
                    .constructMapType(LinkedHashMap.class, String.class, Object.class));
        } catch (JsonProcessingException ignored) {
            return new LinkedHashMap<>();
        }
    }

    private List<EvalTraceStep> parseSteps(List<Object> values) {
        List<EvalTraceStep> steps = new ArrayList<>();
        for (Object value : values) {
            Map<String, Object> map = asMap(value);
            EvalTraceStep step = new EvalTraceStep();
            step.setStepType(string(map, "stepType", "step_type"));
            step.setStatus(string(map, "status"));
            step.setSequenceNo(integer(map, "sequenceNo", "sequence_no"));
            step.setInputSummary(string(map, "inputSummary", "input_summary"));
            step.setOutputSummary(string(map, "outputSummary", "output_summary"));
            step.setRequestPayloadJson(string(map, "requestPayloadJson", "request_payload_json"));
            step.setResponsePayloadJson(string(map, "responsePayloadJson", "response_payload_json"));
            step.setMetadataJson(string(map, "metadataJson", "metadata_json"));
            step.setLatencyMs(longValue(map, "latencyMs", "latency_ms"));
            step.setTotalTokens(integer(map, "totalTokens", "total_tokens"));
            step.setErrorCode(string(map, "errorCode", "error_code"));
            step.setErrorMessage(string(map, "errorMessage", "error_message"));
            steps.add(step);
        }
        return steps;
    }

    private List<EvalTraceToolCall> parseToolCalls(List<Object> values) {
        List<EvalTraceToolCall> calls = new ArrayList<>();
        for (Object value : values) {
            Map<String, Object> map = asMap(value);
            EvalTraceToolCall call = new EvalTraceToolCall();
            call.setToolName(string(map, "toolName", "tool_name"));
            call.setStatus(string(map, "status"));
            call.setRequestSummary(string(map, "requestSummary", "request_summary"));
            call.setResponseSummary(string(map, "responseSummary", "response_summary"));
            call.setMetadataJson(string(map, "metadataJson", "metadata_json"));
            call.setErrorCode(string(map, "errorCode", "error_code"));
            call.setErrorMessage(string(map, "errorMessage", "error_message"));
            calls.add(call);
        }
        return calls;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return objectMapper.convertValue(map, LinkedHashMap.class);
        }
        return new LinkedHashMap<>();
    }

    private List<Object> asList(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return new ArrayList<>();
    }

    private String string(Map<String, Object> map, String... keys) {
        Object value = value(map, keys);
        return value == null ? null : String.valueOf(value);
    }

    private Integer integer(Map<String, Object> map, String... keys) {
        Object value = value(map, keys);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private Long longValue(Map<String, Object> map, String... keys) {
        Object value = value(map, keys);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return Long.parseLong(String.valueOf(value));
    }

    private Object value(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key)) {
                return map.get(key);
            }
        }
        return null;
    }
}
