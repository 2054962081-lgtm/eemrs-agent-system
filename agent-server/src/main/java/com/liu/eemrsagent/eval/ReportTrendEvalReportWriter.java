package com.liu.eemrsagent.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ReportTrendEvalReportWriter {
    private final ObjectMapper objectMapper;

    public ReportTrendEvalReportWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
    }

    public OutputFiles write(Path outputDir, List<ReportTrendEvalResult> results, Metadata metadata) throws IOException {
        Files.createDirectories(outputDir);
        List<ReportTrendEvalResult> sorted = results.stream().sorted(Comparator.comparing(ReportTrendEvalResult::getCaseId)).toList();
        Path csv = outputDir.resolve("report_trend_eval_results.csv");
        Path json = outputDir.resolve("report_trend_badcase_analysis.json");
        Path markdown = outputDir.resolve("report_trend_eval_report.md");
        Files.writeString(csv, csv(sorted), StandardCharsets.UTF_8);
        Files.writeString(json, objectMapper.writeValueAsString(badcase(sorted)), StandardCharsets.UTF_8);
        Files.writeString(markdown, markdown(sorted, metadata), StandardCharsets.UTF_8);
        return new OutputFiles(csv, json, markdown);
    }

    private String csv(List<ReportTrendEvalResult> results) {
        String header = "case_id,source,scenario,category,run_id,analysis_id,status,abnormal_detection_correct,trend_direction_correct,context_link_correct,suggested_department_correct,privacy_pass,cloud_response_valid,doctor_summary_present,patient_explanation_present,contextual_interpretation_present,trace_complete,failure_stage,failure_reason,expected_abnormal_codes,actual_abnormal_codes,expected_trends,actual_trends,expected_context_links,actual_context_links,latency_ms,total_tokens,created_at\n";
        return header + results.stream().map(r -> String.join(",",
                csv(r.getCaseId()), csv(r.getSource()), csv(r.getScenario()), csv(r.getCategory()),
                csv(r.getRunId()), csv(r.getAnalysisId()), csv(r.getStatus()),
                csv(r.getAbnormalDetectionCorrect()), csv(r.getTrendDirectionCorrect()), csv(r.getContextLinkCorrect()),
                csv(r.getSuggestedDepartmentCorrect()), csv(r.getPrivacyPass()), csv(r.getCloudResponseValid()),
                csv(r.getDoctorSummaryPresent()), csv(r.getPatientExplanationPresent()), csv(r.getContextualInterpretationPresent()),
                csv(r.getTraceComplete()), csv(r.getFailureStage()), csv(r.getFailureReason()),
                csv(r.getExpectedAbnormalCodes()), csv(r.getActualAbnormalCodes()), csv(r.getExpectedTrends()), csv(r.getActualTrends()),
                csv(r.getExpectedContextLinks()), csv(r.getActualContextLinks()), csv(r.getLatencyMs()), csv(r.getTotalTokens()), csv(r.getCreatedAt())
        )).collect(Collectors.joining("\n")) + "\n";
    }

    private Map<String, Object> badcase(List<ReportTrendEvalResult> results) {
        List<ReportTrendEvalResult> failed = results.stream().filter(r -> !"PASSED".equals(r.getStatus())).toList();
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("summary", Map.of("total_cases", results.size(), "passed_cases", results.size() - failed.size(), "failed_cases", failed.size()));
        root.put("failure_stage_distribution", failed.stream().collect(Collectors.groupingBy(r -> r.getFailureStage().name(), LinkedHashMap::new, Collectors.counting())));
        List<Map<String, Object>> badCases = new ArrayList<>();
        for (ReportTrendEvalResult r : failed) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("case_id", r.getCaseId());
            item.put("run_id", r.getRunId());
            item.put("analysis_id", r.getAnalysisId());
            item.put("failure_stage", r.getFailureStage().name());
            item.put("failure_reason", r.getFailureReason());
            item.put("suggested_fix", suggestedFix(r.getFailureStage()));
            badCases.add(item);
        }
        root.put("bad_cases", badCases);
        return root;
    }

    private String markdown(List<ReportTrendEvalResult> results, Metadata metadata) {
        List<ReportTrendEvalResult> failed = results.stream().filter(r -> !"PASSED".equals(r.getStatus())).toList();
        StringBuilder b = new StringBuilder();
        b.append("# 报告纵向分析 Harness V2 评测报告\n\n");
        b.append("- 评测时间：").append(LocalDateTime.now()).append('\n');
        b.append("- 数据集版本：").append(metadata.datasetVersion()).append('\n');
        b.append("- case 文件：").append(metadata.caseFile()).append('\n');
        b.append("- 总 case 数：").append(results.size()).append('\n');
        b.append("- 通过数：").append(results.size() - failed.size()).append('\n');
        b.append("- 失败数：").append(failed.size()).append("\n\n");
        b.append("## 指标概览\n\n");
        b.append("- 异常识别准确率：").append(rate(results, ReportTrendEvalResult::getAbnormalDetectionCorrect)).append('\n');
        b.append("- 趋势判断准确率：").append(rate(results, ReportTrendEvalResult::getTrendDirectionCorrect)).append('\n');
        b.append("- 上下文关联准确率：").append(rate(results, ReportTrendEvalResult::getContextLinkCorrect)).append('\n');
        b.append("- 建议科室准确率：").append(rate(results, ReportTrendEvalResult::getSuggestedDepartmentCorrect)).append('\n');
        b.append("- 隐私通过率：").append(rate(results, ReportTrendEvalResult::getPrivacyPass)).append('\n');
        b.append("- 云端响应有效率：").append(rate(results, ReportTrendEvalResult::getCloudResponseValid)).append('\n');
        b.append("- 医生摘要存在率：").append(rate(results, ReportTrendEvalResult::getDoctorSummaryPresent)).append('\n');
        b.append("- 患者解释存在率：").append(rate(results, ReportTrendEvalResult::getPatientExplanationPresent)).append('\n');
        b.append("- 上下文解释存在率：").append(rate(results, ReportTrendEvalResult::getContextualInterpretationPresent)).append('\n');
        b.append("- Trace 完整率：").append(rate(results, ReportTrendEvalResult::getTraceComplete)).append("\n\n");
        b.append("## 失败阶段分布\n\n");
        failed.stream().collect(Collectors.groupingBy(ReportTrendEvalResult::getFailureStage, LinkedHashMap::new, Collectors.counting()))
                .forEach((stage, count) -> b.append("- ").append(stage).append("：").append(count).append('\n'));
        b.append("\n## Top Bad Cases\n\n");
        failed.stream().limit(10).forEach(r -> b.append("- ").append(r.getCaseId()).append("｜").append(r.getScenario()).append("｜").append(r.getFailureStage()).append("｜").append(r.getFailureReason()).append('\n'));
        b.append("\n## 后续建议\n\n");
        b.append("- 针对失败 case 使用 run_id 查看脱敏 Trace evidence。\n");
        b.append("- 优先检查指标标准化、上下文融合和云端 JSON 结构约束。\n\n");
        b.append("## 当前非目标\n\n");
        b.append("- LLM-as-judge\n- 语义相似度匹配\n- 真实云端 integration profile\n- CI 自动评测\n- 前端评测页面\n- 复杂趋势图表\n- OCR 报告评测\n- 影像报告评测\n");
        return b.toString();
    }

    private String suggestedFix(ReportTrendFailureStage stage) {
        return switch (stage) {
            case REPORT_DECRYPT_ERROR -> "检查测试专用解密输入与 LocalReportDecryptor 边界。";
            case REPORT_PARSE_ERROR -> "检查 LocalReportStructuringService 的报告结构化规则。";
            case INDICATOR_NORMALIZE_ERROR -> "检查指标字典和别名映射。";
            case ABNORMAL_DETECTION_ERROR -> "检查异常标志计算和 expected_abnormal_items 映射。";
            case TREND_ANALYSIS_ERROR -> "检查 TrendAnalysisService 的趋势方向规则。";
            case CONTEXT_LOAD_ERROR -> "检查 ReportTrendContextService 上下文读取。";
            case CONTEXT_FUSION_ERROR -> "检查上下文 payload 和 contextLinks 生成要求。";
            case CLOUD_PAYLOAD_PRIVACY_ERROR -> "检查 CloudPayloadPrivacyGuard 与输出脱敏。";
            case CLOUD_MODEL_ERROR -> "检查云端模型调用 mock 和错误处理。";
            case CLOUD_RESPONSE_INVALID -> "检查云端 JSON schema。";
            case RESULT_ENCRYPT_ERROR -> "检查结果加密存储。";
            case TRACE_INCOMPLETE -> "检查 Report Trend Trace step 覆盖。";
            default -> "查看 Trace evidence 进一步定位。";
        };
    }

    private String rate(List<ReportTrendEvalResult> results, Function<ReportTrendEvalResult, Boolean> getter) {
        List<Boolean> values = results.stream().map(getter).filter(v -> v != null).toList();
        if (values.isEmpty()) return "N/A";
        long pass = values.stream().filter(Boolean::booleanValue).count();
        return String.format("%.4f", pass * 1.0d / values.size());
    }

    private String csv(Object value) {
        if (value == null) return "";
        String text = String.valueOf(value).replace("\"", "\"\"");
        return text.contains(",") || text.contains("\n") || text.contains("\"") ? "\"" + text + "\"" : text;
    }

    public record Metadata(String datasetVersion, String caseFile) {}
    public record OutputFiles(Path evalResultsCsv, Path badcaseAnalysisJson, Path evalReportMd) {}
}
