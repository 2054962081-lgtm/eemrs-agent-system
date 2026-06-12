package com.liu.eemrsagent.trace;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AgentStepRecord(
        Long id,
        Integer schemaVersion,
        String traceId,
        String runId,
        String stepId,
        String parentStepId,
        Integer sequenceNo,
        String stepType,
        String stepName,
        String component,
        String serviceName,
        String modelName,
        String promptVersion,
        String inputSummary,
        String outputSummary,
        String inputHash,
        String outputHash,
        String requestPayloadJson,
        String responsePayloadJson,
        String metadataJson,
        String status,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        Long latencyMs,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        BigDecimal estimatedCost,
        String errorCode,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
