package com.liu.eemrsagent.trace;

import java.util.List;

public interface TraceRepository {

    void insertRun(AgentRunRecord run);

    void updateRunFinished(String runId, String status, long latencyMs, Integer promptTokens, Integer completionTokens,
                           Integer totalTokens, String finalOutputSummary, String errorCode, String errorMessage);

    void updateRunModel(String runId, String modelName);

    void insertStep(AgentStepRecord step);

    void updateStepFinished(String stepId, String status, long latencyMs, String outputSummary, String responsePayloadJson,
                            String metadataJson, Integer promptTokens, Integer completionTokens, Integer totalTokens,
                            String errorCode, String errorMessage);

    void insertToolCall(ToolCallRecord toolCall);

    void updateToolCallFinished(String toolCallId, String status, long latencyMs, String responseSummary,
                                String responsePayloadJson, Integer httpStatus, String errorCode, String errorMessage);

    List<AgentRunRecord> findRuns(TraceRunQuery query);

    AgentRunRecord findRun(String runId);

    List<AgentStepRecord> findSteps(String runId);

    List<ToolCallRecord> findToolCalls(String runId);
}
