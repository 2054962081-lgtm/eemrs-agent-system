package com.liu.eemrsagent.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class TraceRequestFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        TraceContext.State state = TraceContext.currentOrNew(
                request.getHeader(TraceHeaders.TRACE_ID),
                request.getHeader(TraceHeaders.RUN_ID),
                request.getHeader(TraceHeaders.SESSION_ID)
        );
        try (TraceContext.Scope ignored = TraceContext.open(state)) {
            response.setHeader(TraceHeaders.TRACE_ID, state.traceId());
            response.setHeader(TraceHeaders.RUN_ID, state.runId());
            filterChain.doFilter(request, response);
        }
    }
}
