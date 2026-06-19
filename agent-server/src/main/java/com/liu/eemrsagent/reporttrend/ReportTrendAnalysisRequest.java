package com.liu.eemrsagent.reporttrend;

import java.time.LocalDate;
import java.util.List;

public record ReportTrendAnalysisRequest(
        String patientId,
        String sessionId,
        Boolean includePreconsultationContext,
        Boolean includeLongTermHealthContext,
        String reportType,
        LocalDate startDate,
        LocalDate endDate,
        List<String> targetItems,
        String outputMode
) {
    public String normalizedReportType() {
        return reportType == null || reportType.isBlank() ? "LAB" : reportType.trim().toUpperCase();
    }

    public boolean shouldIncludePreconsultationContext() {
        return Boolean.TRUE.equals(includePreconsultationContext);
    }

    public boolean shouldIncludeLongTermHealthContext() {
        return Boolean.TRUE.equals(includeLongTermHealthContext);
    }
}
