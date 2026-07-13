package com.liu.eemrsagent.trace;

import java.time.LocalDateTime;

public record ToolCallRecord(
        Long id,
        Integer schemaVersion,
        String traceId,
        String runId,
        String stepId,
        String toolCallId,
        String toolName,
        String toolType,
        String targetService,
        String targetEndpoint,
        String requestSummary,
        String responseSummary,
        String requestHash,
        String responseHash,
        String requestPayloadJson,
        String responsePayloadJson,
        Integer httpStatus,
        String status,
        Integer retryCount,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        Long latencyMs,
        String errorCode,
        String errorMessage,
        String metadataJson,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
