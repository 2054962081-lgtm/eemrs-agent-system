package com.liu.eemrsagent.trace;

public record TraceStepData(
        Object input,
        Object output,
        Object metadata,
        String modelName,
        String promptVersion,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens
) {
    public static TraceStepData of(Object input, Object output, Object metadata) {
        return new TraceStepData(input, output, metadata, null, null, null, null, null);
    }

    public TraceStepData withTokens(String model, Integer prompt, Integer completion, Integer total) {
        return new TraceStepData(input, output, metadata, model, promptVersion, prompt, completion, total);
    }
}
