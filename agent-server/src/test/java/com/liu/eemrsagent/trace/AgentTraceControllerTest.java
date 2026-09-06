package com.liu.eemrsagent.trace;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgentTraceController.class)
@TestPropertySource(properties = "agent.trace.query-api-enabled=true")
class AgentTraceControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private TraceRepository repository;

    @Test
    void consultationRunDetailReturnsRunStepsAndToolCalls() throws Exception {
        when(repository.findRun("run-consult")).thenReturn(run("run-consult", "eval-C-001", "SUCCESS"));
        when(repository.findSteps("run-consult")).thenReturn(List.of(
                step("run-consult", 1, TraceStepType.RAG_QUERY_BUILD, "SUCCESS", null, null),
                step("run-consult", 2, TraceStepType.QUESTION_PLAN, "SUCCESS", null, null),
                step("run-consult", 3, TraceStepType.MODEL_RESPONSE, "SUCCESS", null, null)
        ));
        when(repository.findToolCalls("run-consult")).thenReturn(List.of(tool("run-consult")));

        mvc.perform(get("/api/agent-traces/runs/run-consult/detail"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.run.runId").value("run-consult"))
                .andExpect(jsonPath("$.data.steps", hasSize(3)))
                .andExpect(jsonPath("$.data.tool_calls", hasSize(1)));
    }

    @Test
    void reportRunDetailSerializesCloudParseAndSchemaStages() throws Exception {
        when(repository.findRun("run-report")).thenReturn(run("run-report", "eval-R-001", "FAILED"));
        when(repository.findSteps("run-report")).thenReturn(List.of(
                step("run-report", 1, TraceStepType.CLOUD_MODEL_REQUEST, "SUCCESS", null, null),
                step("run-report", 2, TraceStepType.CLOUD_MODEL_RESPONSE, "SUCCESS", null, null),
                step("run-report", 3, TraceStepType.CLOUD_RESPONSE_PARSE, "SUCCESS", null, null),
                step("run-report", 4, TraceStepType.CLOUD_SCHEMA_VALIDATE, "SUCCESS", null, null)
        ));
        when(repository.findToolCalls("run-report")).thenReturn(List.of());

        mvc.perform(get("/api/agent-traces/runs/run-report/detail"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.steps[0].stepType").value("CLOUD_MODEL_REQUEST"))
                .andExpect(jsonPath("$.data.steps[1].stepType").value("CLOUD_MODEL_RESPONSE"))
                .andExpect(jsonPath("$.data.steps[2].stepType").value("CLOUD_RESPONSE_PARSE"))
                .andExpect(jsonPath("$.data.steps[3].stepType").value("CLOUD_SCHEMA_VALIDATE"));
    }

    @Test
    void failedStepAndNullFieldsRemainSerializable() throws Exception {
        when(repository.findRun("run-failed")).thenReturn(run("run-failed", null, "FAILED"));
        when(repository.findSteps("run-failed")).thenReturn(List.of(
                step("run-failed", 1, TraceStepType.CLOUD_RESPONSE_PARSE, "FAILED",
                        "CLOUD_RESPONSE_JSON_INVALID", "Cloud response is not valid JSON")
        ));
        when(repository.findToolCalls("run-failed")).thenReturn(List.of());

        mvc.perform(get("/api/agent-traces/runs/run-failed/detail"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.run.sessionId").doesNotExist())
                .andExpect(jsonPath("$.data.steps[0].status").value("FAILED"))
                .andExpect(jsonPath("$.data.steps[0].errorCode").value("CLOUD_RESPONSE_JSON_INVALID"))
                .andExpect(jsonPath("$.data.steps[0].errorMessage").value("Cloud response is not valid JSON"));
    }

    @Test
    void missingRunDoesNotReturnHttp500() throws Exception {
        when(repository.findRun("missing")).thenReturn(null);

        mvc.perform(get("/api/agent-traces/runs/missing/detail"))
                .andExpect(status().isNotFound());
    }

    @Test
    void requestIdLookupReturnsLatestRunDetail() throws Exception {
        when(repository.findRunsByRequestId("eval-report-test-001"))
                .thenReturn(List.of(run("run-recovered", "eval-R-001", "SUCCESS")));
        when(repository.findSteps("run-recovered")).thenReturn(List.of(
                step("run-recovered", 1, TraceStepType.REPORT_CIPHER_QUERY, "SUCCESS", null, null)
        ));
        when(repository.findToolCalls("run-recovered")).thenReturn(List.of());

        mvc.perform(get("/api/agent-traces/lookup/request/eval-report-test-001/detail"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.run.runId").value("run-recovered"))
                .andExpect(jsonPath("$.data.runs", hasSize(1)))
                .andExpect(jsonPath("$.data.run_count").value(1))
                .andExpect(jsonPath("$.data.steps[0].stepType").value("REPORT_CIPHER_QUERY"));
    }

    @Test
    void requestIdLookupReturnsOrderedMultiRoundRunsWithoutHttp500() throws Exception {
        when(repository.findRunsByRequestId("eval-C-004"))
                .thenReturn(List.of(
                        run("run-round-1", "eval-C-004", "SUCCESS"),
                        run("run-round-2", "eval-C-004", "SUCCESS")
                ));
        when(repository.findSteps("run-round-1")).thenReturn(List.of(
                step("run-round-1", 1, TraceStepType.USER_INPUT, "SUCCESS", null, null)
        ));
        when(repository.findSteps("run-round-2")).thenReturn(List.of(
                step("run-round-2", 1, TraceStepType.FINAL_ANSWER, "SUCCESS", null, null)
        ));
        when(repository.findToolCalls("run-round-1")).thenReturn(List.of());
        when(repository.findToolCalls("run-round-2")).thenReturn(List.of());

        mvc.perform(get("/api/agent-traces/lookup/request/eval-C-004/detail"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.run.runId").value("run-round-2"))
                .andExpect(jsonPath("$.data.runs", hasSize(2)))
                .andExpect(jsonPath("$.data.details", hasSize(2)))
                .andExpect(jsonPath("$.data.details[0].run.runId").value("run-round-1"))
                .andExpect(jsonPath("$.data.details[1].run.runId").value("run-round-2"));
    }

    @Test
    void missingRequestIdLookupDoesNotReturnHttp500() throws Exception {
        when(repository.findRunsByRequestId("missing-request")).thenReturn(List.of());

        mvc.perform(get("/api/agent-traces/lookup/request/missing-request/detail"))
                .andExpect(status().isNotFound());
    }

    private AgentRunRecord run(String runId, String sessionId, String status) {
        return new AgentRunRecord(
                1L, 1, "trace-1", runId, sessionId, "user-hash", "agent", "request",
                "prompt", "rag", "model", status, LocalDateTime.now(), LocalDateTime.now(), 12L,
                10, 5, 15, BigDecimal.ZERO, "USD", "v1", "final", null, null,
                null, LocalDateTime.now(), LocalDateTime.now()
        );
    }

    private AgentStepRecord step(String runId, int sequenceNo, TraceStepType type, String status, String errorCode, String errorMessage) {
        return new AgentStepRecord(
                1L, 1, "trace-1", runId, "step-" + sequenceNo, null, sequenceNo, type.name(),
                type.name().toLowerCase(), "agent-server", "eemrs-agent-server", "model", null,
                null, "summary", null, null, null, null, null, status,
                LocalDateTime.now(), LocalDateTime.now(), 1L, null, null, null, null,
                errorCode, errorMessage, LocalDateTime.now(), LocalDateTime.now()
        );
    }

    private ToolCallRecord tool(String runId) {
        return new ToolCallRecord(
                1L, 1, "trace-1", runId, "step-1", "tool-1", "rag", "HTTP",
                "rag", "/rag/retrieve", null, null, null, null, null, null,
                200, "SUCCESS", 0, LocalDateTime.now(), LocalDateTime.now(), 1L,
                null, null, null, LocalDateTime.now(), LocalDateTime.now()
        );
    }
}
