package com.liu.eemrsagent.eval;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ReportTrendTraceEvalRunner {
    private final ReportTrendEvalCaseLoader caseLoader;
    private final ReportTrendResponseParser responseParser;
    private final ReportTrendTraceParser traceParser;
    private final ReportTrendTraceBasedEvaluator evaluator;
    private final ReportTrendBadCaseAttributor attributor;
    private final ReportTrendEvalReportWriter writer;

    public ReportTrendTraceEvalRunner(ObjectMapper objectMapper) {
        this.caseLoader = new ReportTrendEvalCaseLoader(objectMapper);
        this.responseParser = new ReportTrendResponseParser(objectMapper);
        this.traceParser = new ReportTrendTraceParser(objectMapper);
        this.evaluator = new ReportTrendTraceBasedEvaluator();
        this.attributor = new ReportTrendBadCaseAttributor();
        this.writer = new ReportTrendEvalReportWriter(objectMapper);
    }

    public ReportTrendEvalReportWriter.OutputFiles run(Config config) throws IOException {
        List<ReportTrendEvalCase> cases = caseLoader.load(config.caseFile());
        int limit = config.maxCases() <= 0 ? cases.size() : Math.min(cases.size(), config.maxCases());
        List<ReportTrendEvalResult> results = new ArrayList<>();
        for (ReportTrendEvalCase evalCase : cases.stream().limit(limit).toList()) {
            try {
                MockRun mock = mockRun(evalCase);
                ReportTrendActualResult actual = responseParser.parse(mock.response());
                ReportTrendTraceEvidence evidence = traceParser.parse(mock.trace());
                ReportTrendEvalResult result = evaluator.evaluate(evalCase, actual, evidence);
                ReportTrendBadCaseAttributor.Attribution attribution = attributor.attribute(evalCase, evidence, result);
                result.setFailureStage(attribution.stage());
                result.setFailureReason(attribution.reason());
                result.setStatus(attribution.stage() == ReportTrendFailureStage.NONE ? "PASSED" : "FAILED");
                results.add(result);
            } catch (RuntimeException ex) {
                ReportTrendEvalResult failed = new ReportTrendEvalResult();
                failed.setCaseId(evalCase.getCaseId());
                failed.setSource(evalCase.getSource());
                failed.setScenario(evalCase.getScenario());
                failed.setCategory(evalCase.getCategory());
                failed.setFailureStage(ReportTrendFailureStage.UNKNOWN);
                failed.setFailureReason(ex.getMessage());
                results.add(failed);
                if (config.failFast()) throw ex;
            }
        }
        return writer.write(config.outputDir(), results, config.metadata());
    }

    private MockRun mockRun(ReportTrendEvalCase evalCase) {
        String runId = "rt-run-" + evalCase.getCaseId() + "-" + UUID.randomUUID();
        String analysisId = "analysis_" + evalCase.getCaseId().replace("-", "_");
        Map<String, Object> response = mockResponse(evalCase, runId, analysisId);
        EvalTraceDetail trace = mockTrace(evalCase, runId, analysisId);
        return new MockRun(response, trace);
    }

    private Map<String, Object> mockResponse(ReportTrendEvalCase evalCase, String runId, String analysisId) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("analysisId", analysisId);
        response.put("traceRunId", runId);
        response.put("status", "SUCCESS");
        response.put("doctorSummary", "医生端摘要：指标趋势异常，建议结合症状和体格检查判断。");
        response.put("patientExplanation", "患者端解释：部分指标有变化，建议携带报告线下咨询医生。");
        response.put("contextualInterpretation", contextualText(evalCase));
        response.put("abnormalItems", evalCase.getExpected().getExpectedAbnormalItems().stream()
                .map(item -> Map.of("standardCode", item.code(), "standardName", item.name() == null ? item.code() : item.name(), "abnormalFlag", item.expectedFlag()))
                .toList());
        response.put("trendItems", evalCase.getExpected().getExpectedTrends().stream()
                .map(item -> Map.of("code", item.code(), "name", item.code(), "trendDirection", item.expectedDirection(), "latestAbnormalFlag", "HIGH"))
                .toList());
        response.put("contextLinks", evalCase.getExpected().getExpectedContextLinks().stream()
                .map(link -> Map.of("type", link.type(), "symptoms", link.symptoms(), "indicators", link.indicators(), "note", "脱敏上下文提示症状或病史与指标可能相关，需医生结合查体判断。"))
                .toList());
        response.put("followUpQuestions", List.of("近期症状持续多久？", "是否有加重表现？"));
        response.put("recommendation", Map.of(
                "suggestedDepartment", evalCase.getExpected().getExpectedSuggestedDepartment() == null ? "内科" : evalCase.getExpected().getExpectedSuggestedDepartment(),
                "suggestedAction", "建议携带报告线下就诊，由医生进一步判断。"
        ));
        return response;
    }

    private String contextualText(ReportTrendEvalCase evalCase) {
        String text = evalCase.getExpected().getExpectedContextLinks().toString();
        return text.isBlank() || "[]".equals(text) ? "暂无可用上下文，基于报告趋势生成解释。" : "结合脱敏上下文 " + text + "，提示相关线索需进一步判断。";
    }

    private EvalTraceDetail mockTrace(ReportTrendEvalCase evalCase, String runId, String analysisId) {
        EvalTraceDetail detail = new EvalTraceDetail();
        detail.setRun(new LinkedHashMap<>(Map.of("runId", runId, "totalLatencyMs", 180L, "totalTokens", 300, "modelName", "mock-report-trend-model")));
        List<EvalTraceStep> steps = new ArrayList<>();
        int i = 1;
        for (String type : ReportTrendTraceParser.requiredSteps()) {
            steps.add(step(i++, type, "SUCCESS", metadata(type, evalCase, analysisId)));
        }
        detail.setSteps(steps);
        return detail;
    }

    private String metadata(String type, ReportTrendEvalCase evalCase, String analysisId) {
        return switch (type) {
            case "REPORT_ANALYSIS_REQUEST" -> "{\"analysis_id\":\"" + analysisId + "\"}";
            case "REPORT_CIPHER_QUERY" -> "{\"report_count\":3}";
            case "REPORT_STRUCTURING", "INDICATOR_NORMALIZE" -> "{\"indicator_count\":4}";
            case "ABNORMAL_DETECTION" -> "{\"abnormal_count\":" + evalCase.getExpected().getExpectedAbnormalItems().size() + "}";
            case "TREND_ANALYSIS" -> "{\"trend_item_count\":" + evalCase.getExpected().getExpectedTrends().size() + "}";
            case "PRECONSULTATION_CONTEXT_LOAD" -> "{\"context_available\":" + expectsContext(evalCase) + ",\"symptom_tag_count\":" + (expectsContext(evalCase) ? 2 : 0) + "}";
            case "LONG_TERM_HEALTH_CONTEXT_LOAD" -> "{\"context_available\":" + expectsContext(evalCase) + ",\"chronic_disease_tag_count\":" + (expectsContext(evalCase) ? 1 : 0) + "}";
            case "TRIAGE_CONTEXT_LOAD" -> "{\"recommended_department\":\"" + (evalCase.getExpected().getExpectedSuggestedDepartment() == null ? "内科" : evalCase.getExpected().getExpectedSuggestedDepartment()) + "\"}";
            case "CONTEXT_FUSION" -> "{\"context_used\":\"metadata-only\",\"context_payload_hash\":\"sha256:mock\"}";
            case "CLOUD_PAYLOAD_BUILD" -> "{\"payload_hash\":\"sha256:mock\",\"privacy_guard_status\":\"SUCCESS\"}";
            case "CLOUD_MODEL_REQUEST", "CLOUD_MODEL_RESPONSE" -> "{\"response_hash\":\"sha256:mock\",\"model_name\":\"mock-report-trend-model\"}";
            case "CLOUD_RESPONSE_VALIDATE" -> "{\"cloud_response_valid\":true}";
            default -> "{}";
        };
    }

    private boolean expectsContext(ReportTrendEvalCase evalCase) {
        return !evalCase.getExpected().getExpectedContextLinks().isEmpty();
    }

    private EvalTraceStep step(int sequence, String type, String status, String metadataJson) {
        EvalTraceStep step = new EvalTraceStep();
        step.setSequenceNo(sequence);
        step.setStepType(type);
        step.setStatus(status);
        step.setMetadataJson(metadataJson);
        step.setResponsePayloadJson(metadataJson);
        step.setLatencyMs(10L);
        step.setTotalTokens(15);
        return step;
    }

    private record MockRun(Map<String, Object> response, EvalTraceDetail trace) {}
    public record Config(String caseFile, Path outputDir, int maxCases, boolean failFast, ReportTrendEvalReportWriter.Metadata metadata) {}
}
