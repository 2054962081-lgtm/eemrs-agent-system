package com.liu.eemrsagent.trace;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EnabledTraceRecorderTest {

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    void recordsRunStepTokensLatencySequenceAndToolLifecycle() {
        InMemoryTraceRepository repository = new InMemoryTraceRepository();
        AgentTraceRecorder recorder = recorder(repository);

        try (TraceRunScope run = recorder.startRun(new TraceRunStart("s1", "u1", "agent", "deep", "p1", "rag1", null, null))) {
            try (TraceStepScope step = recorder.startStep(TraceStepType.MODEL_RESPONSE, "model response", "in", null)) {
                step.success(new TraceStepData(null, "out", null, "m1", null, 10, 5, 15));
            }
            try (ToolCallScope tool = recorder.recordToolCallStart(new ToolCallData("appointment", "HTTP", "eemrs", "/api/a", "req", null, null, null, null, null))) {
                tool.success(new ToolCallData("appointment", "HTTP", "eemrs", "/api/a", null, "ok", 200, null, null, null));
            }
            run.success("final", 10, 5, 15);
        }

        assertThat(repository.runs).hasSize(1);
        assertThat(repository.runStatus).isEqualTo(TraceStatus.SUCCESS.name());
        assertThat(repository.totalTokens).isEqualTo(15);
        assertThat(repository.steps).hasSize(1);
        assertThat(repository.steps.get(0).sequenceNo()).isEqualTo(1);
        assertThat(repository.stepStatus).isEqualTo(TraceStatus.SUCCESS.name());
        assertThat(repository.stepLatency).isNotNegative();
        assertThat(repository.toolCalls).hasSize(1);
        assertThat(repository.toolStatus).isEqualTo(TraceStatus.SUCCESS.name());
    }

    @Test
    void persistenceFailureDoesNotEscape() {
        AgentTraceRecorder recorder = recorder(new ThrowingTraceRepository());

        try (TraceRunScope run = recorder.startRun(new TraceRunStart("s1", "u1", "agent", "deep", "p1", "rag1", null, null))) {
            try (TraceStepScope step = recorder.startStep(TraceStepType.USER_INPUT, "input", "hello", null)) {
                step.success("ok");
            }
            run.success("final", null, null, null);
        }
    }

    @Test
    void noopRecorderKeepsBusinessPathQuiet() {
        AgentTraceRecorder recorder = new NoopTraceRecorder();

        try (TraceRunScope run = recorder.startRun(new TraceRunStart("s1", "u1", "agent", "deep", "p1", "rag1", null, null))) {
            try (TraceStepScope step = recorder.startStep(TraceStepType.USER_INPUT, "input", "hello", null)) {
                step.success("ok");
            }
            run.success("final", null, null, null);
        }
    }

    private AgentTraceRecorder recorder(TraceRepository repository) {
        TraceProperties properties = new TraceProperties();
        properties.setPayloadEnabled(true);
        return new EnabledTraceRecorder(
                repository,
                properties,
                new TracePayloads(new ObjectMapper(), properties, new TraceRedactor()),
                new TraceRedactor()
        );
    }

    private static class InMemoryTraceRepository implements TraceRepository {
        List<AgentRunRecord> runs = new ArrayList<>();
        List<AgentStepRecord> steps = new ArrayList<>();
        List<ToolCallRecord> toolCalls = new ArrayList<>();
        String runStatus;
        String stepStatus;
        String toolStatus;
        long stepLatency;
        Integer totalTokens;

        @Override
        public void insertRun(AgentRunRecord run) {
            runs.add(run);
        }

        @Override
        public void updateRunFinished(String runId, String status, long latencyMs, Integer promptTokens, Integer completionTokens, Integer totalTokens, String finalOutputSummary, String errorCode, String errorMessage) {
            this.runStatus = status;
            this.totalTokens = totalTokens;
        }

        @Override
        public void updateRunModel(String runId, String modelName) {
        }

        @Override
        public void insertStep(AgentStepRecord step) {
            steps.add(step);
        }

        @Override
        public void updateStepFinished(String stepId, String status, long latencyMs, String outputSummary, String responsePayloadJson, String metadataJson, Integer promptTokens, Integer completionTokens, Integer totalTokens, String errorCode, String errorMessage) {
            this.stepStatus = status;
            this.stepLatency = latencyMs;
        }

        @Override
        public void insertToolCall(ToolCallRecord toolCall) {
            toolCalls.add(toolCall);
        }

        @Override
        public void updateToolCallFinished(String toolCallId, String status, long latencyMs, String responseSummary, String responsePayloadJson, Integer httpStatus, String errorCode, String errorMessage) {
            this.toolStatus = status;
        }

        @Override
        public List<AgentRunRecord> findRuns(TraceRunQuery query) {
            return runs;
        }

        @Override
        public AgentRunRecord findRun(String runId) {
            return runs.isEmpty() ? null : runs.get(0);
        }

        @Override
        public List<AgentStepRecord> findSteps(String runId) {
            return steps;
        }

        @Override
        public List<ToolCallRecord> findToolCalls(String runId) {
            return toolCalls;
        }
    }

    private static class ThrowingTraceRepository extends InMemoryTraceRepository {
        @Override
        public void insertRun(AgentRunRecord run) {
            throw new IllegalStateException("db down");
        }

        @Override
        public void insertStep(AgentStepRecord step) {
            throw new IllegalStateException("db down");
        }

        @Override
        public void updateRunFinished(String runId, String status, long latencyMs, Integer promptTokens, Integer completionTokens, Integer totalTokens, String finalOutputSummary, String errorCode, String errorMessage) {
            throw new IllegalStateException("db down");
        }
    }
}
