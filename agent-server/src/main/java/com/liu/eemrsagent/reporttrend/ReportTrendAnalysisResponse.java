package com.liu.eemrsagent.reporttrend;

import java.util.List;

public record ReportTrendAnalysisResponse(
        String analysisId,
        String traceRunId,
        String status,
        String doctorSummary,
        String patientExplanation,
        String contextualInterpretation,
        List<ContextLink> contextLinks,
        List<LabIndicatorItem> abnormalItems,
        List<TrendItem> trendItems,
        List<String> followUpQuestions,
        Recommendation recommendation,
        ContextUsed contextUsed,
        String errorCode,
        String errorMessage
) {
    public static ReportTrendAnalysisResponse fail(String analysisId, String traceRunId, ReportTrendErrorCode errorCode, String message) {
        return new ReportTrendAnalysisResponse(
                analysisId, traceRunId, "FAILED", "", "", "", List.of(), List.of(), List.of(), List.of(),
                new Recommendation("", ""), new ContextUsed(false, false, false), errorCode.name(), message
        );
    }
}
