package com.liu.eemrsagent.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class EvalReportWriter {
    private final ObjectMapper objectMapper;

    public EvalReportWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
    }

    public OutputFiles write(Path outputDir, List<EvalResult> results, Metadata metadata) throws IOException {
        Files.createDirectories(outputDir);
        List<EvalResult> sorted = results.stream()
                .sorted(Comparator.comparing(EvalResult::getCaseId))
                .toList();
        Path csv = outputDir.resolve("eval_results.csv");
        Path json = outputDir.resolve("badcase_analysis.json");
        Path markdown = outputDir.resolve("eval_report.md");
        Files.writeString(csv, csv(sorted), StandardCharsets.UTF_8);
        Files.writeString(json, objectMapper.writeValueAsString(badcase(sorted)), StandardCharsets.UTF_8);
        Files.writeString(markdown, markdown(sorted, metadata), StandardCharsets.UTF_8);
        return new OutputFiles(csv, json, markdown);
    }

    private String csv(List<EvalResult> results) {
        String header = "case_id,source,scenario,category,risk_level,run_id,status,actual_department,expected_departments,"
                + "department_correct,primary_department_correct,must_ask_coverage,red_flag_hit_rate,follow_up_expected,"
                + "follow_up_actual,follow_up_correct,tool_call_expected,tool_call_actual,tool_call_correct,failure_stage,"
                + "failure_reason,latency_ms,total_tokens,trace_complete,created_at\n";
        return header + results.stream().map(result -> String.join(",",
                csv(result.getCaseId()),
                csv(result.getSource()),
                csv(result.getScenario()),
                csv(result.getCategory()),
                csv(result.getRiskLevel()),
                csv(result.getRunId()),
                csv(result.getStatus()),
                csv(result.getActualDepartment()),
                csv(result.getExpectedDepartments()),
                csv(result.getDepartmentCorrect()),
                csv(result.getPrimaryDepartmentCorrect()),
                csv(result.getMustAskCoverage()),
                csv(result.getRedFlagHitRate()),
                csv(result.isFollowUpExpected()),
                csv(result.isFollowUpActual()),
                csv(result.isFollowUpCorrect()),
                csv(result.getToolCallExpected()),
                csv(result.getToolCallActual()),
                csv(result.getToolCallCorrect()),
                csv(result.getFailureStage()),
                csv(result.getFailureReason()),
                csv(result.getLatencyMs()),
                csv(result.getTotalTokens()),
                csv(result.isTraceComplete()),
                csv(result.getCreatedAt())
        )).collect(Collectors.joining("\n")) + "\n";
    }

    private Map<String, Object> badcase(List<EvalResult> results) {
        List<EvalResult> failed = results.stream()
                .filter(result -> !"PASSED".equals(result.getStatus()))
                .toList();
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("summary", Map.of(
                "total_cases", results.size(),
                "passed_cases", results.size() - failed.size(),
                "failed_cases", failed.size()
        ));
        root.put("failure_stage_distribution", failed.stream().collect(Collectors.groupingBy(
                result -> result.getFailureStage().name(),
                LinkedHashMap::new,
                Collectors.counting()
        )));
        List<Map<String, Object>> badCases = new ArrayList<>();
        for (EvalResult result : failed) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("case_id", result.getCaseId());
            item.put("run_id", result.getRunId());
            item.put("failure_stage", result.getFailureStage().name());
            item.put("failure_reason", result.getFailureReason());
            item.put("suggested_fix", suggestedFix(result.getFailureStage()));
            badCases.add(item);
        }
        root.put("bad_cases", badCases);
        return root;
    }

    private String markdown(List<EvalResult> results, Metadata metadata) {
        List<EvalResult> failed = results.stream().filter(result -> !"PASSED".equals(result.getStatus())).toList();
        StringBuilder builder = new StringBuilder();
        builder.append("# Agent Harness V2 Evaluation Report\n\n");
        builder.append("- eval_time: ").append(LocalDateTime.now()).append('\n');
        builder.append("- dataset_version: ").append(metadata.datasetVersion()).append('\n');
        builder.append("- case_file: ").append(metadata.caseFile()).append('\n');
        builder.append("- case_count: ").append(results.size()).append('\n');
        builder.append("- model_name: ").append(metadata.modelName()).append('\n');
        builder.append("- prompt_version: ").append(metadata.promptVersion()).append('\n');
        builder.append("- rag_version: ").append(metadata.ragVersion()).append('\n');
        builder.append("- trace_schema_version: ").append(metadata.traceSchemaVersion()).append('\n');
        builder.append("- git_commit: ").append(metadata.gitCommit()).append("\n\n");
        builder.append("## Summary\n\n");
        builder.append("- passed_cases: ").append(results.size() - failed.size()).append('\n');
        builder.append("- failed_cases: ").append(failed.size()).append('\n');
        builder.append("- department_accuracy: ").append(avgBool(results, EvalResult::getDepartmentCorrect)).append('\n');
        builder.append("- primary_department_accuracy: ").append(avgBool(results, EvalResult::getPrimaryDepartmentCorrect)).append('\n');
        builder.append("- must_ask_avg_coverage: ").append(avgDouble(results, EvalResult::getMustAskCoverage)).append('\n');
        builder.append("- red_flag_avg_hit_rate: ").append(avgDouble(results, EvalResult::getRedFlagHitRate)).append('\n');
        builder.append("- follow_up_accuracy: ").append(avgBool(results, result -> result.isFollowUpCorrect())).append('\n');
        builder.append("- tool_call_accuracy: ").append(avgTool(results)).append('\n');
        builder.append("- trace_complete_rate: ").append(avgBool(results, result -> result.isTraceComplete())).append("\n\n");
        builder.append("## Failure Stage Distribution\n\n");
        failed.stream().collect(Collectors.groupingBy(EvalResult::getFailureStage, LinkedHashMap::new, Collectors.counting()))
                .forEach((stage, count) -> builder.append("- ").append(stage).append(": ").append(count).append('\n'));
        builder.append("\n## Top Bad Cases\n\n");
        failed.stream().limit(10).forEach(result -> builder.append("- ")
                .append(result.getCaseId()).append(" | ")
                .append(result.getScenario()).append(" | ")
                .append(result.getFailureStage()).append(" | ")
                .append(result.getFailureReason()).append('\n'));
        builder.append("\n## Improvement Notes\n\n");
        builder.append("- Use run_id to inspect trace detail for each bad case.\n");
        builder.append("- Reports intentionally omit complete user medical text and keep only case_id, scenario, and metric summaries.\n\n");
        builder.append("## Not Implemented\n\n");
        builder.append("- LLM-as-judge scoring\n");
        builder.append("- Semantic similarity matching\n");
        builder.append("- Trace Grading\n");
        builder.append("- CI automatic evaluation\n");
        builder.append("- Frontend evaluation page\n");
        builder.append("- OpenTelemetry\n");
        builder.append("- Automatic alerts\n");
        builder.append("- Longitudinal report analysis\n");
        return builder.toString();
    }

    private String suggestedFix(FailureStage stage) {
        return switch (stage) {
            case RETRIEVAL_ERROR -> "Check RAG red_flag retrieval and chunk metadata.";
            case QUESTION_PLAN_ERROR -> "Check QuestionPlanBuilder mapping.";
            case MODEL_OUTPUT_ERROR -> "Check prompt adherence to planned follow-up questions.";
            case POST_PROCESS_ERROR -> "Check structured output parser and fallback mapping.";
            case FOLLOW_UP_DECISION_ERROR -> "Check follow-up decision rules.";
            case TOOL_DECISION_ERROR -> "Check registration confirmation guard.";
            case TOOL_EXECUTION_ERROR -> "Check doctor-list tool timeout/error handling.";
            case TRACE_INCOMPLETE -> "Check trace recorder coverage.";
            default -> "Inspect trace detail manually.";
        };
    }

    private String avgBool(List<EvalResult> results, java.util.function.Function<EvalResult, Boolean> getter) {
        List<Boolean> values = results.stream().map(getter).filter(value -> value != null).toList();
        if (values.isEmpty()) {
            return "N/A";
        }
        long matched = values.stream().filter(Boolean::booleanValue).count();
        return scale(matched * 1.0d / values.size());
    }

    private String avgDouble(List<EvalResult> results, java.util.function.ToDoubleFunction<EvalResult> getter) {
        return scale(results.stream().mapToDouble(getter).average().orElse(0.0d));
    }

    private String avgTool(List<EvalResult> results) {
        List<EvalResult> observable = results.stream()
                .filter(result -> !"NOT_OBSERVABLE_IN_CURRENT_ARCHITECTURE".equals(result.getToolCallCorrect()))
                .toList();
        if (observable.isEmpty()) {
            return "N/A";
        }
        long matched = observable.stream().filter(result -> "true".equals(result.getToolCallCorrect())).count();
        return scale(matched * 1.0d / observable.size());
    }

    private String scale(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).toPlainString();
    }

    private String csv(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value).replace("\"", "\"\"");
        if (text.contains(",") || text.contains("\n") || text.contains("\"")) {
            return "\"" + text + "\"";
        }
        return text;
    }

    public record Metadata(String datasetVersion, String caseFile, String modelName, String promptVersion,
                           String ragVersion, String traceSchemaVersion, String gitCommit) {
    }

    public record OutputFiles(Path evalResultsCsv, Path badcaseAnalysisJson, Path evalReportMd) {
    }
}
