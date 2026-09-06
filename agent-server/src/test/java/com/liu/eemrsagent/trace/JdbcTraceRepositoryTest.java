package com.liu.eemrsagent.trace;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JdbcTraceRepositoryTest {

    @Test
    void requestIdLookupFallsBackToLikeThenFiltersExactlyInJava() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        JdbcTraceRepository repository = new JdbcTraceRepository(jdbcTemplate);
        String requestId = "eval-C-004";
        AgentRunRecord matching = run("run-match", "{\"request_id\":\"eval-C-004\"}");
        AgentRunRecord falsePositive = run("run-noise", "{\"request_id\":\"eval-C-004-extra\"}");

        when(jdbcTemplate.query(contains("JSON_UNQUOTE"), any(RowMapper.class), eq(requestId)))
                .thenThrow(new DataAccessResourceFailureException("JSON_EXTRACT disabled"));
        when(jdbcTemplate.query(contains("metadata_json LIKE"), any(RowMapper.class), any()))
                .thenReturn(List.of(matching, falsePositive));

        List<AgentRunRecord> runs = repository.findRunsByRequestId(requestId);

        assertThat(runs).extracting(AgentRunRecord::runId).containsExactly("run-match");
    }

    private AgentRunRecord run(String runId, String metadataJson) {
        return new AgentRunRecord(
                1L, 1, "trace-1", runId, "session", "user-hash", "agent", "request",
                "prompt", "rag", "model", "SUCCESS", LocalDateTime.now(), LocalDateTime.now(), 12L,
                10, 5, 15, BigDecimal.ZERO, "USD", "v1", "final", null, null,
                metadataJson, LocalDateTime.now(), LocalDateTime.now()
        );
    }
}
