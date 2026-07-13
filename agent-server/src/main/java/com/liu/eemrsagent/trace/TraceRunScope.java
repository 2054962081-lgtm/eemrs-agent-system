package com.liu.eemrsagent.trace;

public class TraceRunScope implements AutoCloseable {

    private final EnabledTraceRecorder recorder;
    private final TraceContext.State state;
    private final long startNanos;
    private final boolean noop;
    private boolean finished;

    static TraceRunScope noop(TraceContext.State state) {
        return new TraceRunScope(null, state, System.nanoTime(), true);
    }

    TraceRunScope(EnabledTraceRecorder recorder, TraceContext.State state, long startNanos, boolean noop) {
        this.recorder = recorder;
        this.state = state;
        this.startNanos = startNanos;
        this.noop = noop;
    }

    public String runId() {
        return state.runId();
    }

    public String traceId() {
        return state.traceId();
    }

    public void success(Object finalOutput, Integer promptTokens, Integer completionTokens, Integer totalTokens) {
        if (finished) {
            return;
        }
        finished = true;
        if (!noop) {
            recorder.finishRun(state, startNanos, TraceStatus.SUCCESS,
                    new TraceStepData(null, finalOutput, null, null, null, promptTokens, completionTokens, totalTokens),
                    null, null);
        }
    }

    public void fail(String errorCode, String errorMessage) {
        if (finished) {
            return;
        }
        finished = true;
        if (!noop) {
            recorder.finishRun(state, startNanos, TraceStatus.FAILED,
                    new TraceStepData(null, null, null, null, null, null, null, null), errorCode, errorMessage);
        }
    }

    public void updateModel(String modelName) {
        if (!noop) {
            recorder.updateRunModel(state.runId(), modelName);
        }
    }

    @Override
    public void close() {
        if (!finished) {
            fail(TraceErrorCode.UNKNOWN_ERROR.name(), "Trace run closed before explicit success or failure");
        }
        TraceContext.clear();
    }
}
