package com.liu.eemrsagent.trace;

public record ToolCallData(
        String toolName,
        String toolType,
        String targetService,
        String targetEndpoint,
        Object request,
        Object response,
        Integer httpStatus,
        String errorCode,
        String errorMessage,
        Object metadata
) {
}
