package com.liu.eemrsagent.reporttrend;

import java.util.List;
import java.util.Map;

public record ReportTrendContext(
        Map<String, Object> symptomContextSummary,
        Map<String, Object> healthContextSummary,
        Map<String, Object> triageContextSummary,
        ContextUsed contextUsed,
        List<String> symptomTags,
        List<String> chronicDiseaseTags,
        String recommendedDepartment
) {
    public static ReportTrendContext empty() {
        return new ReportTrendContext(
                Map.of("available", false),
                Map.of("available", false),
                Map.of("available", false),
                new ContextUsed(false, false, false),
                List.of(),
                List.of(),
                ""
        );
    }
}
