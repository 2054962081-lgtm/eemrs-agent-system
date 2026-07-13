package com.liu.eemrsagent.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PreConsultationTraceEvalRunner {
    private final EvalCaseLoader caseLoader;
    private final TraceBasedEvaluator evaluator;
    private final BadCaseAttributor attributor;
    private final EvalReportWriter reportWriter;
    private final TraceDetailClient traceDetailClient;

    public PreConsultationTraceEvalRunner(ObjectMapper objectMapper, TraceDetailClient traceDetailClient) {
        ObjectMapper mapper = objectMapper.copy().registerModule(new JavaTimeModule());
        this.caseLoader = new EvalCaseLoader(mapper);
        this.evaluator = new TraceBasedEvaluator(mapper);
        this.attributor = new BadCaseAttributor(mapper);
        this.reportWriter = new EvalReportWriter(mapper);
        this.traceDetailClient = traceDetailClient;
    }

    public EvalReportWriter.OutputFiles run(Config config) throws IOException {
        List<EvalCase> cases = caseLoader.load(config.caseFile());
        int limit = config.maxCases() <= 0 ? cases.size() : Math.min(config.maxCases(), cases.size());
        List<EvalResult> results = new ArrayList<>();
        for (EvalCase evalCase : cases.stream().limit(limit).toList()) {
            try {
                String runId = "eval-" + evalCase.getCaseId() + "-" + UUID.randomUUID();
                EvalTraceDetail detail = traceDetailClient instanceof MockTraceDetailClient mock
                        ? mock.getDetail(runId, evalCase)
                        : traceDetailClient.getDetail(runId);
                EvalResult result = evaluator.evaluate(evalCase, detail);
                BadCaseAttributor.Attribution attribution = attributor.attribute(evalCase, detail, result);
                result.setFailureStage(attribution.stage());
                result.setFailureReason(attribution.reason());
                result.setStatus(attribution.stage() == FailureStage.NONE ? "PASSED" : "FAILED");
                results.add(result);
            } catch (RuntimeException ex) {
                EvalResult failed = new EvalResult();
                failed.setCaseId(evalCase.getCaseId());
                failed.setSource(evalCase.getSource());
                failed.setScenario(evalCase.getScenario());
                failed.setCategory(evalCase.getCategory());
                failed.setRiskLevel(evalCase.getExpected().getRiskLevel());
                failed.setStatus("FAILED");
                failed.setFailureStage(FailureStage.UNKNOWN);
                failed.setFailureReason(ex.getMessage());
                results.add(failed);
                if (config.failFast()) {
                    throw ex;
                }
            }
        }
        return reportWriter.write(config.outputDir(), results, config.metadata());
    }

    public static PreConsultationTraceEvalRunner mockRunner() {
        return new PreConsultationTraceEvalRunner(new ObjectMapper(), new MockTraceDetailClient());
    }

    public record Config(String caseFile, Path outputDir, int maxCases, boolean failFast,
                         EvalReportWriter.Metadata metadata) {
    }

    public static class MockTraceDetailClient implements TraceDetailClient {
        @Override
        public EvalTraceDetail getDetail(String runId) {
            throw new UnsupportedOperationException("Mock runner requires EvalCase context.");
        }

        public EvalTraceDetail getDetail(String runId, EvalCase evalCase) {
            EvalTraceDetail detail = new EvalTraceDetail();
            Map<String, Object> run = new LinkedHashMap<>();
            run.put("runId", runId);
            run.put("totalLatencyMs", 128L);
            run.put("totalTokens", 256);
            run.put("modelName", "mock-model");
            detail.setRun(run);
            EvalExpectedResult expected = evalCase.getExpected();
            detail.setSteps(List.of(
                    step(1, "USER_INPUT", "SUCCESS", "metadata-only", "metadata-only", null, null, null),
                    step(2, "RAG_RETRIEVAL", "SUCCESS", null, "doc_type=red_flag chunk_id=mock score=0.91 symptom_inquiry",
                            null, null, keysJson(expected.getExpectedRedFlags())),
                    step(3, "QUESTION_PLAN", "SUCCESS", null, expected.getExpectedMustAsk().toString(),
                            null, null, keysJson(expected.getExpectedMustAsk())),
                    step(4, "MODEL_RESPONSE", "SUCCESS", null, expected.getExpectedMustAsk().toString(),
                            null, null, null),
                    step(5, "POST_PROCESS", "SUCCESS", null, expected.getPrimaryDepartment(),
                            null, "{\"actual_department\":\"" + escape(expected.getPrimaryDepartment()) + "\"}", "{\"json_parse_failed\":false}"),
                    step(6, "FOLLOW_UP_DECISION", "SUCCESS", null, String.valueOf(expected.isShouldFollowUp()),
                            null, "{\"need_follow_up\":" + expected.isShouldFollowUp() + "}", null),
                    step(7, "FINAL_ANSWER", "SUCCESS", null, expected.getExpectedMustAsk().toString() + expected.getExpectedRedFlags(),
                            null, null, null)
            ));
            if (Boolean.TRUE.equals(expected.getExpectedToolCall())) {
                EvalTraceToolCall call = new EvalTraceToolCall();
                call.setToolName("doctor_list");
                call.setStatus("SUCCESS");
                detail.setToolCalls(List.of(call));
            }
            return detail;
        }

        private EvalTraceStep step(int sequence, String type, String status, String input, String output,
                                   String requestJson, String responseJson, String metadataJson) {
            EvalTraceStep step = new EvalTraceStep();
            step.setSequenceNo(sequence);
            step.setStepType(type);
            step.setStatus(status);
            step.setInputSummary(input);
            step.setOutputSummary(output);
            step.setRequestPayloadJson(requestJson);
            step.setResponsePayloadJson(responseJson);
            step.setMetadataJson(metadataJson);
            step.setLatencyMs(10L);
            step.setTotalTokens(20);
            return step;
        }

        private String keysJson(List<EvalKeyPoint> points) {
            String joined = points.stream()
                    .map(point -> "{\"key\":\"" + escape(point.getKey()) + "\",\"description\":\"" + escape(point.getDescription()) + "\"}")
                    .reduce((left, right) -> left + "," + right)
                    .orElse("");
            return "{\"points\":[" + joined + "]}";
        }

        private String escape(String value) {
            return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }
}
