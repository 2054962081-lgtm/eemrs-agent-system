package com.liu.eemrsagent.trace;

import java.util.UUID;

public final class TraceIds {

    private TraceIds() {
    }

    public static String newTraceId() {
        return "tr-" + uuid();
    }

    public static String newRunId() {
        return "run-" + uuid();
    }

    public static String newStepId() {
        return "step-" + uuid();
    }

    public static String newToolCallId() {
        return "tool-" + uuid();
    }

    private static String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
