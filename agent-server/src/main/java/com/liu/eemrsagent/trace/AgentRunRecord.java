package com.liu.eemrsagent.trace;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AgentRunRecord(
        Long id,
        Integer schemaVersion,
        String traceId,
        String runId,
        String sessionId,
        String userIdHash,
        String agentName,
        String requestType,
        String promptVersion,
        String ragVersion,
        String modelName,
        String status,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        Long totalLatencyMs,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        BigDecimal estimatedCost,
        String costCurrency,
        String costConfigVersion,
        String finalOutputSummary,
        String errorCode,
        String errorMessage,
        String metadataJson,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
