package com.liu.eemrsagent.trace;

import org.slf4j.MDC;

import java.util.Objects;
import java.util.Optional;

public final class TraceContext {

    public static final String MDC_TRACE_ID = "trace_id";
    public static final String MDC_RUN_ID = "run_id";
    public static final String MDC_STEP_ID = "step_id";
    public static final String MDC_SESSION_ID = "session_id";

    private static final ThreadLocal<State> CURRENT = new ThreadLocal<>();

    private TraceContext() {
    }

    public static Optional<State> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static State currentOrNew(String traceId, String runId, String sessionId) {
        State existing = CURRENT.get();
        if (existing != null) {
            return existing;
        }
        return new State(
                blankToDefault(traceId, TraceIds.newTraceId()),
                blankToDefault(runId, TraceIds.newRunId()),
                blankToNull(sessionId),
                null,
                null,
                "deep-preconsultation-agent"
        );
    }

    public static Scope open(State state) {
        State previous = CURRENT.get();
        set(state);
        return new Scope(previous);
    }

    public static void set(State state) {
        CURRENT.set(Objects.requireNonNull(state, "state"));
        putMdc(MDC_TRACE_ID, state.traceId());
        putMdc(MDC_RUN_ID, state.runId());
        putMdc(MDC_STEP_ID, state.currentStepId());
        putMdc(MDC_SESSION_ID, state.sessionId());
    }

    public static void clear() {
        CURRENT.remove();
        MDC.remove(MDC_TRACE_ID);
        MDC.remove(MDC_RUN_ID);
        MDC.remove(MDC_STEP_ID);
        MDC.remove(MDC_SESSION_ID);
    }

    public static State withStep(String stepId) {
        State current = CURRENT.get();
        if (current == null) {
            current = new State(TraceIds.newTraceId(), TraceIds.newRunId(), null, null, null, "unknown-agent");
        }
        State next = current.withCurrentStepId(stepId);
        set(next);
        return next;
    }

    private static void putMdc(String key, String value) {
        if (value == null || value.isBlank()) {
            MDC.remove(key);
        } else {
            MDC.put(key, value);
        }
    }

    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record State(
            String traceId,
            String runId,
            String sessionId,
            String currentStepId,
            String userIdHash,
            String agentName
    ) {
        public State withCurrentStepId(String stepId) {
            return new State(traceId, runId, sessionId, stepId, userIdHash, agentName);
        }

        public State withUserIdHash(String value) {
            return new State(traceId, runId, sessionId, currentStepId, value, agentName);
        }

        public State withAgentName(String value) {
            return new State(traceId, runId, sessionId, currentStepId, userIdHash, value);
        }
    }

    public static final class Scope implements AutoCloseable {
        private final State previous;
        private boolean closed;

        private Scope(State previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (previous == null) {
                clear();
            } else {
                set(previous);
            }
        }
    }
}
