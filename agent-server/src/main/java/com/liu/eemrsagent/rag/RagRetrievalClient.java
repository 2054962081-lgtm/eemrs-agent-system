package com.liu.eemrsagent.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import com.liu.eemrsagent.trace.TraceContext;
import com.liu.eemrsagent.trace.TraceHeaders;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class RagRetrievalClient {

    public static final String SCENE_PRE_INQUIRY = "pre_inquiry";
    public static final String SCENE_DEEP_INQUIRY = "deep_inquiry";
    public static final String SCENE_MEDICAL_RECORD = "medical_record";

    private static final Logger log = LoggerFactory.getLogger(RagRetrievalClient.class);

    private final RagProperties properties;

    public RagRetrievalClient(RagProperties properties) {
        this.properties = properties;
    }

    public List<RagChunk> retrieve(String query, String scene) {
        return retrieveWithMetadata(query, scene).chunks();
    }

    public RagRetrievalResult retrieveWithMetadata(String query, String scene) {
        if (!properties.isEnabled() || query == null || query.isBlank()) {
            return RagRetrievalResult.empty();
        }
        long start = System.nanoTime();
        try {
            RagRetrieveResponse response = restClient()
                    .post()
                    .uri(properties.getRetrievePath())
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> TraceContext.current().ifPresent(context -> {
                        headers.set(TraceHeaders.TRACE_ID, context.traceId());
                        headers.set(TraceHeaders.RUN_ID, context.runId());
                        if (context.currentStepId() != null) {
                            headers.set(TraceHeaders.STEP_ID, context.currentStepId());
                        }
                        if (context.sessionId() != null) {
                            headers.set(TraceHeaders.SESSION_ID, context.sessionId());
                        }
                    }))
                    .body(new RagRetrieveRequest(query, topKForScene(scene), includeDocTypesForScene(scene), scene))
                    .retrieve()
                    .body(RagRetrieveResponse.class);
            if (response == null || !response.success()) {
                return handleFailure("RAG service returned success=false: " + (response == null ? "null response" : response.errorMessage()));
            }
            List<RagChunk> chunks = response.chunks() == null ? List.of() : response.chunks();
            Map<String, Integer> docTypeCounts = response.docTypeCounts() == null ? Map.of() : response.docTypeCounts();
            if (properties.isDebugLog()) {
                log.info(
                        "RAG retrieved scene={}, inputLength={}, elapsedMs={}, hitCount={}, docTypes={}, queryExpansion={}",
                        scene,
                        query.length(),
                        elapsedMs(start),
                        chunks.size(),
                        docTypeCounts.isEmpty() ? docTypeCounts(chunks) : docTypeCounts,
                        Boolean.TRUE.equals(response.usedQueryExpansion())
                );
            }
            return new RagRetrievalResult(
                    chunks,
                    response.expandedQuery() == null ? "" : response.expandedQuery(),
                    docTypeCounts,
                    Boolean.TRUE.equals(response.usedQueryExpansion())
            );
        } catch (RuntimeException e) {
            return handleFailure("RAG retrieval failed: " + e.getMessage());
        }
    }

    private RestClient restClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(properties.getTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(properties.getTimeoutMs()));
        return RestClient.builder()
                .baseUrl(trimTrailingSlash(properties.getServiceUrl()))
                .requestFactory(factory)
                .build();
    }

    private RagRetrievalResult handleFailure(String message) {
        if (properties.isDebugLog()) {
            log.warn("{}; failOpen={}", message, properties.isFailOpen());
        }
        if (properties.isFailOpen()) {
            return RagRetrievalResult.empty();
        }
        throw new IllegalStateException(message);
    }

    private Integer topKForScene(String scene) {
        int configured = Math.max(1, properties.getTopK());
        if (SCENE_DEEP_INQUIRY.equals(scene) || SCENE_MEDICAL_RECORD.equals(scene)) {
            return Math.max(configured, 10);
        }
        return configured;
    }

    private List<String> includeDocTypesForScene(String scene) {
        if (SCENE_MEDICAL_RECORD.equals(scene)) {
            return List.of("medical_record_template", "symptom_inquiry", "special_population", "red_flag", "department_triage");
        }
        return properties.getIncludeDocTypes();
    }

    private Map<String, Long> docTypeCounts(List<RagChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return Collections.emptyMap();
        }
        return chunks.stream()
                .collect(Collectors.groupingBy(chunk -> chunk.docType() == null ? "unknown" : chunk.docType(), Collectors.counting()));
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:18080";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
