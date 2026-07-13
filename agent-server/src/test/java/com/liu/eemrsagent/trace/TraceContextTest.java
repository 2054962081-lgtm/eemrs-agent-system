package com.liu.eemrsagent.trace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

class TraceContextTest {

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    void opensAndClearsMdcContext() {
        TraceContext.State state = new TraceContext.State("trace-1", "run-1", "session-1", "step-1", "user", "agent");

        try (TraceContext.Scope ignored = TraceContext.open(state)) {
            assertThat(TraceContext.current()).contains(state);
            assertThat(MDC.get(TraceContext.MDC_TRACE_ID)).isEqualTo("trace-1");
            assertThat(MDC.get(TraceContext.MDC_RUN_ID)).isEqualTo("run-1");
            assertThat(MDC.get(TraceContext.MDC_STEP_ID)).isEqualTo("step-1");
            assertThat(MDC.get(TraceContext.MDC_SESSION_ID)).isEqualTo("session-1");
        }

        assertThat(TraceContext.current()).isEmpty();
        assertThat(MDC.get(TraceContext.MDC_TRACE_ID)).isNull();
    }

    @Test
    void scopesDoNotLeakBetweenRequests() {
        try (TraceContext.Scope ignored = TraceContext.open(new TraceContext.State("trace-a", "run-a", null, null, null, "agent"))) {
            assertThat(TraceContext.current().orElseThrow().traceId()).isEqualTo("trace-a");
        }
        try (TraceContext.Scope ignored = TraceContext.open(new TraceContext.State("trace-b", "run-b", null, null, null, "agent"))) {
            assertThat(TraceContext.current().orElseThrow().traceId()).isEqualTo("trace-b");
        }
    }
}
