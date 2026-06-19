package com.liu.eemrsagent.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BadCaseAttributorTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TraceBasedEvaluator evaluator = new TraceBasedEvaluator(objectMapper);
    private final BadCaseAttributor attributor = new BadCaseAttributor(objectMapper);

    @Test
    void attributesRetrievalError() {
        EvalTraceDetail detail = EvalTestFixtures.passingTrace();
        detail.firstStep("RAG_RETRIEVAL").orElseThrow().setOutputSummary("no relevant docs");
        detail.firstStep("RAG_RETRIEVAL").orElseThrow().setMetadataJson(null);
        detail.firstStep("RAG_RETRIEVAL").orElseThrow().setResponsePayloadJson(null);

        assertThat(stage(detail)).isEqualTo(FailureStage.RETRIEVAL_ERROR);
    }

    @Test
    void attributesQuestionPlanError() {
        EvalTraceDetail detail = EvalTestFixtures.passingTrace();
        detail.firstStep("QUESTION_PLAN").orElseThrow().setOutputSummary("only chest_pain_duration");
        detail.firstStep("QUESTION_PLAN").orElseThrow().setMetadataJson(null);
        detail.firstStep("QUESTION_PLAN").orElseThrow().setResponsePayloadJson(null);

        assertThat(stage(detail)).isEqualTo(FailureStage.QUESTION_PLAN_ERROR);
    }

    @Test
    void attributesModelOutputError() {
        EvalTraceDetail detail = EvalTestFixtures.passingTrace();
        detail.firstStep("MODEL_RESPONSE").orElseThrow().setOutputSummary("generic answer");
        detail.firstStep("FINAL_ANSWER").orElseThrow().setOutputSummary("generic final chest_pain");

        assertThat(stage(detail)).isEqualTo(FailureStage.MODEL_OUTPUT_ERROR);
    }

    @Test
    void attributesPostProcessError() {
        EvalTraceDetail detail = EvalTestFixtures.passingTrace();
        detail.firstStep("POST_PROCESS").orElseThrow()
                .setMetadataJson("{\"json_parse_failed\":true,\"fallback_parse_used\":true}");

        assertThat(stage(detail)).isEqualTo(FailureStage.POST_PROCESS_ERROR);
    }

    @Test
    void attributesFollowUpDecisionError() {
        EvalTraceDetail detail = EvalTestFixtures.passingTrace();
        detail.firstStep("FOLLOW_UP_DECISION").orElseThrow().setResponsePayloadJson("{\"need_follow_up\":false}");

        assertThat(stage(detail)).isEqualTo(FailureStage.FOLLOW_UP_DECISION_ERROR);
    }

    @Test
    void attributesToolDecisionError() {
        EvalCase evalCase = EvalTestFixtures.chestPainCase();
        evalCase.getExpected().setExpectedToolCall(true);
        EvalTraceDetail detail = EvalTestFixtures.passingTrace();

        EvalResult result = evaluator.evaluate(evalCase, detail);
        assertThat(attributor.attribute(evalCase, detail, result).stage()).isEqualTo(FailureStage.TOOL_DECISION_ERROR);
    }

    @Test
    void attributesToolExecutionError() {
        EvalCase evalCase = EvalTestFixtures.chestPainCase();
        evalCase.getExpected().setExpectedToolCall(true);
        EvalTraceDetail detail = EvalTestFixtures.passingTrace();
        EvalTraceToolCall call = new EvalTraceToolCall();
        call.setToolName("doctor_list");
        call.setStatus("FAILED");
        detail.setToolCalls(List.of(call));

        EvalResult result = evaluator.evaluate(evalCase, detail);
        assertThat(attributor.attribute(evalCase, detail, result).stage()).isEqualTo(FailureStage.TOOL_EXECUTION_ERROR);
    }

    @Test
    void attributesTraceIncomplete() {
        EvalTraceDetail detail = EvalTestFixtures.passingTrace();
        detail.setRun(java.util.Map.of());

        assertThat(stage(detail)).isEqualTo(FailureStage.TRACE_INCOMPLETE);
    }

    @Test
    void attributesNotObservableRegistrationRefusal() {
        EvalCase evalCase = EvalTestFixtures.chestPainCase();
        evalCase.getExpected().setExpectedToolCall(null);
        evalCase.setTags(List.of("registration_refusal"));
        EvalTraceDetail detail = EvalTestFixtures.passingTrace();
        EvalResult result = evaluator.evaluate(evalCase, detail);

        assertThat(attributor.attribute(evalCase, detail, result).stage())
                .isEqualTo(FailureStage.NOT_OBSERVABLE_IN_CURRENT_ARCHITECTURE);
    }

    private FailureStage stage(EvalTraceDetail detail) {
        EvalCase evalCase = EvalTestFixtures.chestPainCase();
        EvalResult result = evaluator.evaluate(evalCase, detail);
        return attributor.attribute(evalCase, detail, result).stage();
    }
}
