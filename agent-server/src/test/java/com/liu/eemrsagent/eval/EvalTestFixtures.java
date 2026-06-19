package com.liu.eemrsagent.eval;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class EvalTestFixtures {
    private EvalTestFixtures() {
    }

    static EvalCase chestPainCase() {
        EvalCase evalCase = new EvalCase();
        evalCase.setCaseId("PC-CHEST-TEST");
        evalCase.setSource("added_harness_v2");
        evalCase.setScenario("chest pain");
        evalCase.setCategory("red_flag");
        EvalExpectedResult expected = new EvalExpectedResult();
        expected.setExpectedDepartments(List.of("急诊科", "心内科"));
        expected.setPrimaryDepartment("急诊科");
        expected.setExpectedMustAsk(List.of(
                new EvalKeyPoint("chest_pain_duration", "胸痛持续时间"),
                new EvalKeyPoint("dyspnea", "呼吸困难")
        ));
        expected.setExpectedRedFlags(List.of(new EvalKeyPoint("chest_pain", "胸痛")));
        expected.setShouldFollowUp(true);
        expected.setShouldRecommendRegistration(false);
        expected.setExpectedToolCall(false);
        expected.setRiskLevel("high");
        evalCase.setExpected(expected);
        return evalCase;
    }

    static EvalTraceDetail passingTrace() {
        EvalTraceDetail detail = new EvalTraceDetail();
        Map<String, Object> run = new LinkedHashMap<>();
        run.put("runId", "run-test-1");
        run.put("totalLatencyMs", 100);
        run.put("totalTokens", 321);
        detail.setRun(run);
        detail.setSteps(List.of(
                step(1, "USER_INPUT", "metadata-only", null, null),
                step(2, "RAG_RETRIEVAL", null, "doc_type=red_flag chunk_id=c1 score=0.9 chest_pain", "{\"red_flags\":[\"chest_pain\"]}"),
                step(3, "QUESTION_PLAN", null, "chest_pain_duration dyspnea", "{\"must_ask\":[\"chest_pain_duration\",\"dyspnea\"]}"),
                step(4, "MODEL_RESPONSE", null, "ask chest_pain_duration and dyspnea", null),
                step(5, "POST_PROCESS", null, "急诊科", "{\"actual_department\":\"急诊科\",\"json_parse_failed\":false}"),
                step(6, "FOLLOW_UP_DECISION", null, "true", "{\"need_follow_up\":true}"),
                step(7, "FINAL_ANSWER", null, "chest_pain_duration dyspnea chest_pain", null)
        ));
        return detail;
    }

    static EvalTraceStep step(int sequence, String type, String input, String output, String responseJson) {
        EvalTraceStep step = new EvalTraceStep();
        step.setSequenceNo(sequence);
        step.setStepType(type);
        step.setStatus("SUCCESS");
        step.setInputSummary(input);
        step.setOutputSummary(output);
        step.setResponsePayloadJson(responseJson);
        return step;
    }
}
