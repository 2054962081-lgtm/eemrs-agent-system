package com.liu.eemrsserver.trace;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
public class AgentTraceHeaderFilter extends OncePerRequestFilter {

    private static final String TRACE_ID = "X-Agent-Trace-Id";
    private static final String RUN_ID = "X-Agent-Run-Id";
    private static final String STEP_ID = "X-Agent-Step-Id";
    private static final String SESSION_ID = "X-Agent-Session-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            put("trace_id", request.getHeader(TRACE_ID));
            put("run_id", request.getHeader(RUN_ID));
            put("step_id", request.getHeader(STEP_ID));
            put("session_id", request.getHeader(SESSION_ID));
            echo(response, TRACE_ID, request.getHeader(TRACE_ID));
            echo(response, RUN_ID, request.getHeader(RUN_ID));
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("trace_id");
            MDC.remove("run_id");
            MDC.remove("step_id");
            MDC.remove("session_id");
        }
    }

    private void put(String key, String value) {
        if (value != null && !value.trim().isEmpty()) {
            MDC.put(key, value.trim());
        }
    }

    private void echo(HttpServletResponse response, String name, String value) {
        if (value != null && !value.trim().isEmpty()) {
            response.setHeader(name, value.trim());
        }
    }
}
