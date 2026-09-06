package com.liu.eemrsserver.trace;

import com.liu.eemrsserver.domain.DoctorInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AgentToolTraceService {

    private static final Logger log = LoggerFactory.getLogger(AgentToolTraceService.class);
    private static final String TRACE_ID = "X-Agent-Trace-Id";
    private static final String RUN_ID = "X-Agent-Run-Id";
    private static final String STEP_ID = "X-Agent-Step-Id";
    private static final String SESSION_ID = "X-Agent-Session-Id";

    private final JdbcTemplate jdbcTemplate;

    public AgentToolTraceService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void recordDoctorQuery(HttpServletRequest request, String department, List<DoctorInfo> doctors,
                                  long startedNanos, Integer httpStatus, String status,
                                  String errorCode, String errorMessage) {
        String runId = header(request, RUN_ID);
        if (runId == null) {
            return;
        }
        String traceId = header(request, TRACE_ID);
        if (traceId == null) {
            traceId = "trace-from-eemrs-" + UUID.randomUUID();
        }
        String stepId = header(request, STEP_ID);
        String sessionId = header(request, SESSION_ID);
        int resultCount = doctors == null ? 0 : doctors.size();
        long latencyMs = (System.nanoTime() - startedNanos) / 1_000_000;
        String toolCallId = "tool-" + UUID.randomUUID();
        String requestSummary = "{\"department\":\"" + safeJson(department) + "\",\"capture_level\":\"METADATA_ONLY\"}";
        String responseSummary = "{\"result_count\":" + resultCount + "}";
        String metadataJson = "{\"should_call_tool\":true,"
                + "\"tool_name\":\"query_department_doctors\","
                + "\"reason_code\":\"USER_CONFIRMED_APPOINTMENT\","
                + "\"department\":\"" + safeJson(department) + "\","
                + "\"session_id\":\"" + safeJson(sessionId) + "\"}";
        try {
            jdbcTemplate.update(
                    "INSERT INTO tool_call (schema_version, trace_id, run_id, step_id, tool_call_id, tool_name, tool_type, "
                            + "target_service, target_endpoint, request_summary, response_summary, request_hash, response_hash, "
                            + "http_status, status, retry_count, started_at, ended_at, latency_ms, error_code, error_message, metadata_json) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, SHA2(COALESCE(?, ''), 256), SHA2(COALESCE(?, ''), 256), "
                            + "?, ?, ?, ?, NOW(), ?, ?, ?, ?)",
                    1,
                    traceId,
                    runId,
                    stepId,
                    toolCallId,
                    "query_department_doctors",
                    "HTTP",
                    "eemrs-server",
                    "/api/doctors",
                    requestSummary,
                    responseSummary,
                    requestSummary,
                    responseSummary,
                    httpStatus,
                    status,
                    0,
                    LocalDateTime.now(),
                    latencyMs,
                    errorCode,
                    safe(errorMessage, 1000),
                    metadataJson
            );
        } catch (Exception e) {
            log.warn("TRACE_TOOL_CALL_PERSIST_FAILED: {}", safe(e.getMessage(), 200));
        }
    }

    private String header(HttpServletRequest request, String name) {
        if (request == null) {
            return null;
        }
        String value = request.getHeader(name);
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private String safeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String safe(String value, int limit) {
        if (value == null) {
            return null;
        }
        String text = value.replaceAll("(?i)(Authorization|api[-_]?key|token|cookie|password|secret)\\s*[:=]\\s*[^,;\\s}\\\"]+", "$1=[REDACTED]");
        return text.length() <= limit ? text : text.substring(0, limit);
    }
}
