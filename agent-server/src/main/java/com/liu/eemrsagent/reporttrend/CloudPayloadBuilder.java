package com.liu.eemrsagent.reporttrend;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class CloudPayloadBuilder {
    public Map<String, Object> build(ReportTrendAnalysisRequest request, List<StructuredLabReport> reports,
                                     List<TrendItem> trendItems, ReportTrendContext context) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("analysis_task", "LAB_REPORT_TREND_SUMMARY");
        payload.put("report_type", request.normalizedReportType());
        payload.put("date_range", Map.of(
                "start", request.startDate().toString(),
                "end", request.endDate().toString()
        ));
        payload.put("coarse_patient_context", Map.of(
                "report_count", reports.size()
        ));
        payload.put("normalized_items", reports.stream()
                .flatMap(report -> report.items().stream())
                .filter(item -> !item.standardCode().startsWith("UNKNOWN_"))
                .map(this::indicatorPayload)
                .distinct()
                .toList());
        payload.put("trend_results", trendItems.stream()
                .map(this::trendPayload)
                .toList());
        payload.put("abnormal_summary", trendItems.stream()
                .filter(item -> item.latestAbnormalFlag() == AbnormalFlag.HIGH || item.latestAbnormalFlag() == AbnormalFlag.LOW)
                .map(this::trendPayload)
                .toList());
        payload.put("symptom_context_summary", context.symptomContextSummary());
        payload.put("health_context_summary", context.healthContextSummary());
        payload.put("triage_context_summary", context.triageContextSummary());
        payload.put("output_requirements", List.of(
                "doctorSummary", "patientExplanation", "contextualInterpretation", "contextLinks",
                "followUpQuestions", "suggestedDepartment", "suggestedAction"
        ));
        return payload;
    }

    private Map<String, Object> indicatorPayload(LabIndicatorItem item) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("code", item.standardCode());
        out.put("indicator_name", item.standardName());
        out.put("unit", item.unit());
        out.put("reference_low", item.referenceLow());
        out.put("reference_high", item.referenceHigh());
        return out;
    }

    private Map<String, Object> trendPayload(TrendItem item) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("code", item.code());
        out.put("indicator_name", item.name());
        out.put("unit", item.unit());
        out.put("point_count", item.pointCount());
        out.put("first_date", dateText(item.firstDate()));
        out.put("latest_date", dateText(item.latestDate()));
        out.put("first_value", item.firstValue());
        out.put("latest_value", item.latestValue());
        out.put("previous_value", item.previousValue());
        out.put("min_value", item.minValue());
        out.put("max_value", item.maxValue());
        out.put("change_absolute", item.changeAbsolute());
        out.put("change_percent", item.changePercent());
        out.put("trend_direction", enumName(item.trendDirection()));
        out.put("latest_abnormal_flag", enumName(item.latestAbnormalFlag()));
        out.put("abnormal_count", item.abnormalCount());
        out.put("consecutive_abnormal_count", item.consecutiveAbnormalCount());
        out.put("first_abnormal_date", dateText(item.firstAbnormalDate()));
        out.put("latest_abnormal_date", dateText(item.latestAbnormalDate()));
        return out;
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private String dateText(LocalDate value) {
        return value == null ? null : value.toString();
    }
}
