package com.liu.eemrsagent.trace;

public class ToolCallScope implements AutoCloseable {

    private final EnabledTraceRecorder recorder;
    private final String toolCallId;
    private final long startNanos;
    private final boolean noop;
    private boolean finished;

    static ToolCallScope noop() {
        return new ToolCallScope(null, null, System.nanoTime(), true);
    }

    ToolCallScope(EnabledTraceRecorder recorder, String toolCallId, long startNanos, boolean noop) {
        this.recorder = recorder;
        this.toolCallId = toolCallId;
        this.startNanos = startNanos;
        this.noop = noop;
    }

    public String toolCallId() {
        return toolCallId;
    }

    public void success(ToolCallData data) {
        finish(TraceStatus.SUCCESS, data);
    }

    public void fail(ToolCallData data) {
        finish(TraceStatus.FAILED, data);
    }

    public void timeout(ToolCallData data) {
        finish(TraceStatus.TIMEOUT, data);
    }

    private void finish(TraceStatus status, ToolCallData data) {
        if (finished) {
            return;
        }
        finished = true;
        if (!noop) {
            recorder.finishToolCall(toolCallId, startNanos, status, data);
        }
    }

    @Override
    public void close() {
        if (!finished) {
            fail(new ToolCallData(null, null, null, null, null, null, null,
                    TraceErrorCode.UNKNOWN_ERROR.name(), "Tool call closed before explicit success or failure", null));
        }
    }
}
