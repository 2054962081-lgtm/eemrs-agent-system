package com.liu.eemrsagent.trace;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
public class JdbcTraceRepository implements TraceRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcTraceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void insertRun(AgentRunRecord run) {
        jdbcTemplate.update("""
                INSERT INTO agent_run (
                    schema_version, trace_id, run_id, session_id, user_id_hash, agent_name, request_type,
                    prompt_version, rag_version, model_name, status, started_at, metadata_json
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                run.schemaVersion(), run.traceId(), run.runId(), run.sessionId(), run.userIdHash(), run.agentName(),
                run.requestType(), run.promptVersion(), run.ragVersion(), run.modelName(), run.status(),
                run.startedAt(), run.metadataJson());
    }

    @Override
    public void updateRunFinished(String runId, String status, long latencyMs, Integer promptTokens, Integer completionTokens,
                                  Integer totalTokens, String finalOutputSummary, String errorCode, String errorMessage) {
        jdbcTemplate.update("""
                UPDATE agent_run
                SET status = ?, ended_at = NOW(), total_latency_ms = ?, prompt_tokens = ?, completion_tokens = ?,
                    total_tokens = ?, final_output_summary = ?, error_code = ?, error_message = ?, updated_at = NOW()
                WHERE run_id = ?
                """, status, latencyMs, promptTokens, completionTokens, totalTokens, finalOutputSummary, errorCode, errorMessage, runId);
    }

    @Override
    public void updateRunModel(String runId, String modelName) {
        jdbcTemplate.update("UPDATE agent_run SET model_name = ?, updated_at = NOW() WHERE run_id = ?", modelName, runId);
    }

    @Override
    public void insertStep(AgentStepRecord step) {
        jdbcTemplate.update("""
                INSERT INTO agent_step (
                    schema_version, trace_id, run_id, step_id, parent_step_id, sequence_no, step_type, step_name,
                    component, service_name, model_name, prompt_version, input_summary, input_hash, request_payload_json,
                    metadata_json, status, started_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                step.schemaVersion(), step.traceId(), step.runId(), step.stepId(), step.parentStepId(), step.sequenceNo(),
                step.stepType(), step.stepName(), step.component(), step.serviceName(), step.modelName(), step.promptVersion(),
                step.inputSummary(), step.inputHash(), step.requestPayloadJson(), step.metadataJson(), step.status(), step.startedAt());
    }

    @Override
    public void updateStepFinished(String stepId, String status, long latencyMs, String outputSummary, String responsePayloadJson,
                                   String metadataJson, Integer promptTokens, Integer completionTokens, Integer totalTokens,
                                   String errorCode, String errorMessage) {
        jdbcTemplate.update("""
                UPDATE agent_step
                SET status = ?, ended_at = NOW(), latency_ms = ?, output_summary = ?, output_hash = SHA2(COALESCE(?, ''), 256),
                    response_payload_json = ?, metadata_json = COALESCE(?, metadata_json), prompt_tokens = ?,
                    completion_tokens = ?, total_tokens = ?, error_code = ?, error_message = ?, updated_at = NOW()
                WHERE step_id = ?
                """,
                status, latencyMs, outputSummary, outputSummary, responsePayloadJson, metadataJson, promptTokens,
                completionTokens, totalTokens, errorCode, errorMessage, stepId);
    }

    @Override
    public void insertToolCall(ToolCallRecord toolCall) {
        jdbcTemplate.update("""
                INSERT INTO tool_call (
                    schema_version, trace_id, run_id, step_id, tool_call_id, tool_name, tool_type, target_service,
                    target_endpoint, request_summary, request_hash, request_payload_json, http_status, status,
                    retry_count, started_at, metadata_json
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, SHA2(COALESCE(?, ''), 256), ?, ?, ?, ?, ?, ?)
                """,
                toolCall.schemaVersion(), toolCall.traceId(), toolCall.runId(), toolCall.stepId(), toolCall.toolCallId(),
                toolCall.toolName(), toolCall.toolType(), toolCall.targetService(), toolCall.targetEndpoint(),
                toolCall.requestSummary(), toolCall.requestSummary(), toolCall.requestPayloadJson(), toolCall.httpStatus(),
                toolCall.status(), toolCall.retryCount(), toolCall.startedAt(), toolCall.metadataJson());
    }

    @Override
    public void updateToolCallFinished(String toolCallId, String status, long latencyMs, String responseSummary,
                                       String responsePayloadJson, Integer httpStatus, String errorCode, String errorMessage) {
        jdbcTemplate.update("""
                UPDATE tool_call
                SET status = ?, ended_at = NOW(), latency_ms = ?, response_summary = ?,
                    response_hash = SHA2(COALESCE(?, ''), 256), response_payload_json = ?,
                    http_status = COALESCE(?, http_status), error_code = ?, error_message = ?, updated_at = NOW()
                WHERE tool_call_id = ?
                """, status, latencyMs, responseSummary, responseSummary, responsePayloadJson, httpStatus, errorCode, errorMessage, toolCallId);
    }

    @Override
    public List<AgentRunRecord> findRuns(TraceRunQuery query) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, schema_version, trace_id, run_id, session_id, user_id_hash, agent_name, request_type,
                       prompt_version, rag_version, model_name, status, started_at, ended_at, total_latency_ms,
                       prompt_tokens, completion_tokens, total_tokens, estimated_cost, cost_currency, cost_config_version,
                       final_output_summary, error_code, error_message, metadata_json, created_at, updated_at
                FROM agent_run WHERE 1 = 1
                """);
        List<Object> args = new ArrayList<>();
        appendFilter(sql, args, "session_id", query.sessionId());
        appendFilter(sql, args, "user_id_hash", query.userIdHash());
        appendFilter(sql, args, "agent_name", query.agentName());
        appendFilter(sql, args, "status", query.status());
        appendFilter(sql, args, "model_name", query.modelName());
        if (query.startTime() != null) {
            sql.append(" AND started_at >= ?");
            args.add(query.startTime());
        }
        if (query.endTime() != null) {
            sql.append(" AND started_at <= ?");
            args.add(query.endTime());
        }
        sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
        args.add(query.limit());
        args.add(query.offset());
        return jdbcTemplate.query(sql.toString(), runMapper(), args.toArray());
    }

    @Override
    public AgentRunRecord findRun(String runId) {
        List<AgentRunRecord> runs = jdbcTemplate.query("""
                SELECT id, schema_version, trace_id, run_id, session_id, user_id_hash, agent_name, request_type,
                       prompt_version, rag_version, model_name, status, started_at, ended_at, total_latency_ms,
                       prompt_tokens, completion_tokens, total_tokens, estimated_cost, cost_currency, cost_config_version,
                       final_output_summary, error_code, error_message, metadata_json, created_at, updated_at
                FROM agent_run WHERE run_id = ?
                """, runMapper(), runId);
        return runs.isEmpty() ? null : runs.get(0);
    }

    @Override
    public List<AgentStepRecord> findSteps(String runId) {
        return jdbcTemplate.query("""
                SELECT * FROM agent_step WHERE run_id = ? ORDER BY sequence_no ASC
                """, stepMapper(), runId);
    }

    @Override
    public List<ToolCallRecord> findToolCalls(String runId) {
        return jdbcTemplate.query("""
                SELECT * FROM tool_call WHERE run_id = ? ORDER BY created_at ASC
                """, toolMapper(), runId);
    }

    private void appendFilter(StringBuilder sql, List<Object> args, String column, String value) {
        if (value != null && !value.isBlank()) {
            sql.append(" AND ").append(column).append(" = ?");
            args.add(value);
        }
    }

    private RowMapper<AgentRunRecord> runMapper() {
        return (rs, rowNum) -> new AgentRunRecord(
                rs.getLong("id"), rs.getInt("schema_version"), rs.getString("trace_id"), rs.getString("run_id"),
                rs.getString("session_id"), rs.getString("user_id_hash"), rs.getString("agent_name"), rs.getString("request_type"),
                rs.getString("prompt_version"), rs.getString("rag_version"), rs.getString("model_name"), rs.getString("status"),
                toLocalDateTime(rs, "started_at"), toLocalDateTime(rs, "ended_at"), getLong(rs, "total_latency_ms"),
                getInt(rs, "prompt_tokens"), getInt(rs, "completion_tokens"), getInt(rs, "total_tokens"),
                rs.getBigDecimal("estimated_cost"), rs.getString("cost_currency"), rs.getString("cost_config_version"),
                rs.getString("final_output_summary"), rs.getString("error_code"), rs.getString("error_message"),
                rs.getString("metadata_json"), toLocalDateTime(rs, "created_at"), toLocalDateTime(rs, "updated_at"));
    }

    private RowMapper<AgentStepRecord> stepMapper() {
        return (rs, rowNum) -> new AgentStepRecord(
                rs.getLong("id"), rs.getInt("schema_version"), rs.getString("trace_id"), rs.getString("run_id"),
                rs.getString("step_id"), rs.getString("parent_step_id"), getInt(rs, "sequence_no"), rs.getString("step_type"),
                rs.getString("step_name"), rs.getString("component"), rs.getString("service_name"), rs.getString("model_name"),
                rs.getString("prompt_version"), rs.getString("input_summary"), rs.getString("output_summary"),
                rs.getString("input_hash"), rs.getString("output_hash"), rs.getString("request_payload_json"),
                rs.getString("response_payload_json"), rs.getString("metadata_json"), rs.getString("status"),
                toLocalDateTime(rs, "started_at"), toLocalDateTime(rs, "ended_at"), getLong(rs, "latency_ms"),
                getInt(rs, "prompt_tokens"), getInt(rs, "completion_tokens"), getInt(rs, "total_tokens"),
                rs.getBigDecimal("estimated_cost"), rs.getString("error_code"), rs.getString("error_message"),
                toLocalDateTime(rs, "created_at"), toLocalDateTime(rs, "updated_at"));
    }

    private RowMapper<ToolCallRecord> toolMapper() {
        return (rs, rowNum) -> new ToolCallRecord(
                rs.getLong("id"), rs.getInt("schema_version"), rs.getString("trace_id"), rs.getString("run_id"),
                rs.getString("step_id"), rs.getString("tool_call_id"), rs.getString("tool_name"), rs.getString("tool_type"),
                rs.getString("target_service"), rs.getString("target_endpoint"), rs.getString("request_summary"),
                rs.getString("response_summary"), rs.getString("request_hash"), rs.getString("response_hash"),
                rs.getString("request_payload_json"), rs.getString("response_payload_json"), getInt(rs, "http_status"),
                rs.getString("status"), getInt(rs, "retry_count"), toLocalDateTime(rs, "started_at"),
                toLocalDateTime(rs, "ended_at"), getLong(rs, "latency_ms"), rs.getString("error_code"),
                rs.getString("error_message"), rs.getString("metadata_json"), toLocalDateTime(rs, "created_at"),
                toLocalDateTime(rs, "updated_at"));
    }

    private LocalDateTime toLocalDateTime(ResultSet rs, String column) throws SQLException {
        var timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private Integer getInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private Long getLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
