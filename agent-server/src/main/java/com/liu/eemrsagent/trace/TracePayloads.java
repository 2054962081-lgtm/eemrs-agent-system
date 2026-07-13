package com.liu.eemrsagent.trace;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class TracePayloads {

    private final ObjectMapper objectMapper;
    private final TraceProperties properties;
    private final TraceRedactor redactor;

    public TracePayloads(ObjectMapper objectMapper, TraceProperties properties, TraceRedactor redactor) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.redactor = redactor;
    }

    public String summary(Object value) {
        return truncate(redactor.redact(toText(value)), properties.getSummaryMaxLength());
    }

    public String payload(Object value) {
        if (!properties.isPayloadEnabled() || value == null) {
            return null;
        }
        return truncate(redactor.redact(toJson(value)), properties.getPayloadMaxLength());
    }

    public String metadata(Object value) {
        if (value == null) {
            return null;
        }
        return truncate(redactor.redact(toJson(value)), properties.getPayloadMaxLength());
    }

    private String toText(Object value) {
        if (value == null) {
            return null;
        }
        return String.valueOf(value);
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return text;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{\"error\":\"TRACE_SERIALIZE_FAILED\",\"type\":\"" + value.getClass().getSimpleName() + "\"}";
        }
    }

    public String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        int limit = Math.max(1, maxLength);
        if (value.length() <= limit) {
            return value;
        }
        return value.substring(0, limit) + "...[TRUNCATED]";
    }
}
