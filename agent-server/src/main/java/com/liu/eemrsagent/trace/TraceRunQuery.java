package com.liu.eemrsagent.trace;

import java.time.LocalDateTime;

public record TraceRunQuery(
        String sessionId,
        String userIdHash,
        String agentName,
        String status,
        String modelName,
        LocalDateTime startTime,
        LocalDateTime endTime,
        int page,
        int size
) {
    public int offset() {
        return Math.max(0, page) * Math.max(1, size);
    }

    public int limit() {
        return Math.min(100, Math.max(1, size));
    }
}
