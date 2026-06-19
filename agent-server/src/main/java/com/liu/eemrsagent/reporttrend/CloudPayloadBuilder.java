package com.liu.eemrsagent.reporttrend;

import org.springframework.stereotype.Component;

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
        payload.put("trend_results", trendItems);
        payload.put("abnormal_summary", trendItems.stream()
                .filter(item -> item.latestAbnormalFlag() == AbnormalFlag.HIGH || item.latestAbnormalFlag() == AbnormalFlag.LOW)
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
        out.put("name", item.standardName());
        out.put("unit", item.unit());
        out.put("referenceLow", item.referenceLow());
        out.put("referenceHigh", item.referenceHigh());
        return out;
    }
}
