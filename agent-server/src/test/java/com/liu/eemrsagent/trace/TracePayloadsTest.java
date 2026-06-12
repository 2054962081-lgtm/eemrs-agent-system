package com.liu.eemrsagent.trace;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TracePayloadsTest {

    @Test
    void truncatesAndRedactsPayloads() {
        TraceProperties properties = new TraceProperties();
        properties.setPayloadEnabled(true);
        properties.setPayloadMaxLength(20);
        properties.setSummaryMaxLength(40);
        TracePayloads payloads = new TracePayloads(new ObjectMapper(), properties, new TraceRedactor());

        String payload = payloads.payload(Map.of("Authorization", "Bearer very-secret-token", "text", "abcdefghijklmnopqrstuvwxyz"));

        assertThat(payload).contains("[TRUNCATED]");
        assertThat(payload).doesNotContain("very-secret-token");
    }
}
