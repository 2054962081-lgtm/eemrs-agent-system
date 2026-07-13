package com.liu.eemrsagent.trace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

public class EnabledTraceRecorder implements AgentTraceRecorder {

    private static final Logger log = LoggerFactory.getLogger(EnabledTraceRecorder.class);
    private static final int SCHEMA_VERSION = 1;

    private final TraceRepository repository;
    private final TraceProperties properties;
    private final TracePayloads payloads;
    private final TraceRedactor redactor;
    private final AtomicInteger sequence = new AtomicInteger(0);

    public EnabledTraceRecorder(TraceRepository repository, TraceProperties properties, TracePayloads payloads, TraceRedactor redactor) {
        this.repository = repository;
        this.properties = properties;
        this.payloads = payloads;
        this.redactor = redactor;
    }

    @Override
    public TraceRunScope startRun(TraceRunStart start) {
        TraceContext.State incoming = TraceContext.currentOrNew(null, null, start.sessionId());
        String userHash = redactor.hashUserId(start.userId(), properties.getUserHashSalt());
        TraceContext.State state = new TraceContext.State(
                incoming.traceId(),
                incoming.runId(),
                start.sessionId() == null ? incoming.sessionId() : start.sessionId(),
                null,
                userHash,
                start.agentName() == null || start.agentName().isBlank() ? "deep-preconsultation-agent" : start.agentName()
        );
        TraceContext.set(state);
        sequence.set(0);
        runSafely(() -> {
            if (properties.isPersistenceEnabled()) {
                repository.insertRun(new AgentRunRecord(
                        null, SCHEMA_VERSION, state.traceId(), state.runId(), state.sessionId(), state.userIdHash(),
                        state.agentName(), start.requestType(), start.promptVersion(), start.ragVersion(), start.modelName(),
                        TraceStatus.RUNNING.name(), LocalDateTime.now(), null, null, null, null, null, null, null,
                        null, null, null, null, payloads.metadata(start.metadata()), null, null));
            }
        });
        return new TraceRunScope(this, state, System.nanoTime(), false);
    }

    @Override
    public TraceStepScope startStep(TraceStepType type, String name, Object input, Object metadata) {
        TraceContext.State state = TraceContext.current().orElseGet(() -> TraceContext.currentOrNew(null, null, null));
        String stepId = TraceIds.newStepId();
        String parentStepId = state.currentStepId();
        TraceContext.withStep(stepId);
        int sequenceNo = sequence.incrementAndGet();
        runSafely(() -> {
            if (properties.isPersistenceEnabled()) {
                String inputSummary = payloads.summary(input);
                repository.insertStep(new AgentStepRecord(
                        null, SCHEMA_VERSION, state.traceId(), state.runId(), stepId, parentStepId, sequenceNo,
                        type.name(), name, "agent-server", "eemrs-agent-server", null, null,
                        inputSummary, null, inputSummary == null ? null : redactor.stableHash(inputSummary), null,
                        payloads.payload(input), null, payloads.metadata(metadata), TraceStatus.RUNNING.name(),
                        LocalDateTime.now(), null, null, null, null, null, null, null, null, null, null));
            }
        });
        return new TraceStepScope(this, stepId, parentStepId, System.nanoTime(), false);
    }

    @Override
    public void skipStep(TraceStepType type, String name, Object metadata) {
        try (TraceStepScope scope = startStep(type, name, null, metadata)) {
            scope.skip(metadata);
        }
    }

    @Override
    public ToolCallScope recordToolCallStart(ToolCallData data) {
        TraceContext.State state = TraceContext.current().orElseGet(() -> TraceContext.currentOrNew(null, null, null));
        String toolCallId = TraceIds.newToolCallId();
        runSafely(() -> {
            if (properties.isPersistenceEnabled()) {
                String requestSummary = payloads.summary(data.request());
                repository.insertToolCall(new ToolCallRecord(
                        null, SCHEMA_VERSION, state.traceId(), state.runId(), state.currentStepId(), toolCallId,
                        data.toolName(), data.toolType(), data.targetService(), data.targetEndpoint(),
                        requestSummary, null, null, null, payloads.payload(data.request()), null, data.httpStatus(),
                        TraceStatus.RUNNING.name(), 0, LocalDateTime.now(), null, null, null, null,
                        payloads.metadata(data.metadata()), null, null));
            }
        });
        return new ToolCallScope(this, toolCallId, System.nanoTime(), false);
    }

    void finishRun(TraceContext.State state, long startNanos, TraceStatus status, TraceStepData data, String errorCode, String errorMessage) {
        runSafely(() -> {
            if (properties.isPersistenceEnabled()) {
                repository.updateRunFinished(
                        state.runId(),
                        status.name(),
                        elapsedMs(startNanos),
                        data == null ? null : data.promptTokens(),
                        data == null ? null : data.completionTokens(),
                        data == null ? null : data.totalTokens(),
                        data == null ? null : payloads.summary(data.output()),
                        errorCode,
                        payloads.summary(errorMessage)
                );
            }
        });
    }

    void updateRunModel(String runId, String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return;
        }
        runSafely(() -> {
            if (properties.isPersistenceEnabled()) {
                repository.updateRunModel(runId, modelName);
            }
        });
    }

    void finishStep(String stepId, long startNanos, TraceStatus status, TraceStepData data, String errorCode, String errorMessage) {
        runSafely(() -> {
            if (properties.isPersistenceEnabled()) {
                repository.updateStepFinished(
                        stepId,
                        status.name(),
                        elapsedMs(startNanos),
                        data == null ? null : payloads.summary(data.output()),
                        data == null ? null : payloads.payload(data.output()),
                        data == null ? null : payloads.metadata(data.metadata()),
                        data == null ? null : data.promptTokens(),
                        data == null ? null : data.completionTokens(),
                        data == null ? null : data.totalTokens(),
                        errorCode,
                        payloads.summary(errorMessage)
                );
            }
        });
    }

    void finishToolCall(String toolCallId, long startNanos, TraceStatus status, ToolCallData data) {
        runSafely(() -> {
            if (properties.isPersistenceEnabled()) {
                repository.updateToolCallFinished(
                        toolCallId,
                        status.name(),
                        elapsedMs(startNanos),
                        data == null ? null : payloads.summary(data.response()),
                        data == null ? null : payloads.payload(data.response()),
                        data == null ? null : data.httpStatus(),
                        data == null ? null : data.errorCode(),
                        data == null ? null : payloads.summary(data.errorMessage())
                );
            }
        });
    }

    private void runSafely(Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.warn("{}: {}", TraceErrorCode.TRACE_PERSIST_FAILED, payloads.summary(e.getMessage()));
        }
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
