package com.liu.eemrsagent.trace;

public final class TraceHeaders {

    public static final String TRACE_ID = "X-Agent-Trace-Id";
    public static final String RUN_ID = "X-Agent-Run-Id";
    public static final String STEP_ID = "X-Agent-Step-Id";
    public static final String SESSION_ID = "X-Agent-Session-Id";
    public static final String EVAL_RUN_ID = "X-EEMRS-Eval-Run-Id";
    public static final String EVAL_CASE_ID = "X-EEMRS-Eval-Case-Id";
    public static final String REQUEST_ID = "X-Request-Id";

    private TraceHeaders() {
    }
}
