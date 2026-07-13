package com.liu.eemrsagent.trace;

public interface AgentTraceRecorder {

    TraceRunScope startRun(TraceRunStart start);

    TraceStepScope startStep(TraceStepType type, String name, Object input, Object metadata);

    void skipStep(TraceStepType type, String name, Object metadata);

    ToolCallScope recordToolCallStart(ToolCallData data);
}
