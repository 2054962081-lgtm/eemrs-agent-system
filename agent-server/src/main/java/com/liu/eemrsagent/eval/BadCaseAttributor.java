package com.liu.eemrsagent.eval;

import com.fasterxml.jackson.databind.ObjectMapper;

public class BadCaseAttributor {
    private final TraceBasedEvaluator evaluator;

    public BadCaseAttributor(ObjectMapper objectMapper) {
        this.evaluator = new TraceBasedEvaluator(objectMapper);
    }

    public Attribution attribute(EvalCase evalCase, EvalTraceDetail detail, EvalResult result) {
        EvalExpectedResult expected = evalCase.getExpected();
        if (expected.getExpectedToolCall() == null && evalCase.getTags().contains("registration_refusal")) {
            return new Attribution(FailureStage.NOT_OBSERVABLE_IN_CURRENT_ARCHITECTURE,
                    "Registration refusal is not observable in the current backend trace architecture.",
                    "Keep the expected field and add a backend event only when the product flow exposes it.");
        }
        if (!evaluator.isTraceComplete(detail)) {
            return new Attribution(FailureStage.TRACE_INCOMPLETE,
                    "Trace is missing run_id, USER_INPUT, FINAL_ANSWER, or has duplicated sequence numbers.",
                    "Check trace recorder coverage and sequence allocation.");
        }
        if (detail.getToolCalls().stream().anyMatch(call -> containsAny(call.getStatus(), "FAILED", "TIMEOUT"))) {
            return new Attribution(FailureStage.TOOL_EXECUTION_ERROR,
                    "A TOOL_CALL was recorded with FAILED or TIMEOUT status.",
                    "Check downstream doctor-list service timeout and error handling.");
        }
        String ragText = detail.joinedText("RAG_RETRIEVAL");
        String questionPlanText = detail.joinedText("QUESTION_PLAN");
        String modelText = detail.joinedText("MODEL_RESPONSE", "FINAL_ANSWER");
        String postProcessText = detail.joinedText("POST_PROCESS");

        if (!expected.getExpectedRedFlags().isEmpty() && !containsAny(ragText, "red_flag", "red flag", "chunk_id", "doc_type", "score")) {
            return new Attribution(FailureStage.RETRIEVAL_ERROR,
                    "Expected red flags are present, but RAG_RETRIEVAL has no red_flag/chunk/doc metadata.",
                    "Check RAG retrieval query and red_flag document indexing.");
        }
        if (containsAny(ragText, "red_flag", "symptom_inquiry") && !allExpectedContained(questionPlanText, expected.getExpectedMustAsk())) {
            return new Attribution(FailureStage.QUESTION_PLAN_ERROR,
                    "RAG recalled relevant material, but QUESTION_PLAN missed expected must-ask items.",
                    "Check QuestionPlanBuilder mapping from retrieved docs to must-ask slots.");
        }
        if (allExpectedContained(questionPlanText, expected.getExpectedMustAsk()) && !allExpectedContained(modelText, expected.getExpectedMustAsk())) {
            return new Attribution(FailureStage.MODEL_OUTPUT_ERROR,
                    "QUESTION_PLAN contains the must-ask items, but model/final answer did not express them.",
                    "Check prompt constraints and response rendering for planned questions.");
        }
        if (containsAny(postProcessText, "\"json_parse_failed\":true", "\"fallback_parse_used\":true",
                "\"missing_fields\":true", "department:null", "department\":\"\"")) {
            return new Attribution(FailureStage.POST_PROCESS_ERROR,
                    "POST_PROCESS reports parse failure, fallback parsing, missing fields, or empty department.",
                    "Check structured output parser and fallback field mapping.");
        }
        if (!result.isFollowUpCorrect()) {
            return new Attribution(FailureStage.FOLLOW_UP_DECISION_ERROR,
                    "FOLLOW_UP_DECISION does not match expected should_follow_up.",
                    "Check follow-up threshold and stop conditions.");
        }
        if (!"NOT_OBSERVABLE_IN_CURRENT_ARCHITECTURE".equals(result.getToolCallCorrect()) && !"true".equals(result.getToolCallCorrect())) {
            return new Attribution(FailureStage.TOOL_DECISION_ERROR,
                    "Tool call occurrence does not match expected registration confirmation state.",
                    "Check TOOL_DECISION guards before side-effect tool calls.");
        }
        if (resultPasses(result)) {
            return new Attribution(FailureStage.NONE, "", "");
        }
        return new Attribution(FailureStage.UNKNOWN,
                "The result failed, but no deterministic attribution rule matched.",
                "Inspect the trace detail manually and add a narrower attribution rule if it recurs.");
    }

    private boolean resultPasses(EvalResult result) {
        boolean departmentOk = result.getDepartmentCorrect() == null || result.getDepartmentCorrect();
        boolean primaryOk = result.getPrimaryDepartmentCorrect() == null || result.getPrimaryDepartmentCorrect();
        boolean toolOk = "true".equals(result.getToolCallCorrect())
                || "NOT_OBSERVABLE_IN_CURRENT_ARCHITECTURE".equals(result.getToolCallCorrect());
        return departmentOk
                && primaryOk
                && result.getMustAskCoverage() >= 1.0d
                && result.getRedFlagHitRate() >= 1.0d
                && result.isFollowUpCorrect()
                && toolOk;
    }

    private boolean allExpectedContained(String text, java.util.List<EvalKeyPoint> expected) {
        String normalized = text == null ? "" : text.toLowerCase();
        return expected.stream().allMatch(point ->
                containsAny(normalized, point.getKey(), point.getDescription()));
    }

    private boolean containsAny(String text, String... tokens) {
        if (text == null) {
            return false;
        }
        String normalized = text.toLowerCase();
        for (String token : tokens) {
            if (token != null && !token.isBlank() && normalized.contains(token.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    public record Attribution(FailureStage stage, String reason, String suggestedFix) {
    }
}
