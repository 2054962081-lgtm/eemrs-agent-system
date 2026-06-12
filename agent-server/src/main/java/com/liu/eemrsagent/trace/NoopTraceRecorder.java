package com.liu.eemrsagent.trace;

public class NoopTraceRecorder implements AgentTraceRecorder {

    @Override
    public TraceRunScope startRun(TraceRunStart start) {
        TraceContext.State state = TraceContext.currentOrNew(null, null, start == null ? null : start.sessionId());
        return TraceRunScope.noop(state);
    }

    @Override
    public TraceStepScope startStep(TraceStepType type, String name, Object input, Object metadata) {
        return TraceStepScope.noop();
    }

    @Override
    public void skipStep(TraceStepType type, String name, Object metadata) {
    }

    @Override
    public ToolCallScope recordToolCallStart(ToolCallData data) {
        return ToolCallScope.noop();
    }
}
