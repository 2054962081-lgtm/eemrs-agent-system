package com.liu.eemrsagent.trace;

public class TraceStepScope implements AutoCloseable {

    private final EnabledTraceRecorder recorder;
    private final String stepId;
    private final String parentStepId;
    private final long startNanos;
    private final boolean noop;
    private boolean finished;

    static TraceStepScope noop() {
        return new TraceStepScope(null, null, null, System.nanoTime(), true);
    }

    TraceStepScope(EnabledTraceRecorder recorder, String stepId, String parentStepId, long startNanos, boolean noop) {
        this.recorder = recorder;
        this.stepId = stepId;
        this.parentStepId = parentStepId;
        this.startNanos = startNanos;
        this.noop = noop;
    }

    public String stepId() {
        return stepId;
    }

    public void success(Object output) {
        success(TraceStepData.of(null, output, null));
    }

    public void success(TraceStepData data) {
        finish(TraceStatus.SUCCESS, data, null, null);
    }

    public void skip(Object metadata) {
        finish(TraceStatus.SKIPPED, TraceStepData.of(null, null, metadata), null, null);
    }

    public void fail(String errorCode, String errorMessage) {
        finish(TraceStatus.FAILED, TraceStepData.of(null, null, null), errorCode, errorMessage);
    }

    private void finish(TraceStatus status, TraceStepData data, String errorCode, String errorMessage) {
        if (finished) {
            return;
        }
        finished = true;
        if (!noop) {
            recorder.finishStep(stepId, startNanos, status, data, errorCode, errorMessage);
        }
    }

    @Override
    public void close() {
        if (!finished) {
            fail(TraceErrorCode.UNKNOWN_ERROR.name(), "Trace step closed before explicit success or failure");
        }
        if (parentStepId == null || parentStepId.isBlank()) {
            TraceContext.current().ifPresent(state -> TraceContext.set(state.withCurrentStepId(null)));
        } else {
            TraceContext.withStep(parentStepId);
        }
    }
}
