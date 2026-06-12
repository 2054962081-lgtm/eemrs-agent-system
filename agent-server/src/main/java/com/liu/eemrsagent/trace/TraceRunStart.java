package com.liu.eemrsagent.trace;

public record TraceRunStart(
        String sessionId,
        String userId,
        String agentName,
        String requestType,
        String promptVersion,
        String ragVersion,
        String modelName,
        Object metadata
) {
}
