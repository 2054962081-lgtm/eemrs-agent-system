package com.liu.eemrsagent.eval;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ReportFixtureRequest(
        String evalRunId,
        String caseId,
        String patientId,
        String sessionId,
        String reportType,
        List<Record> records
) {
    public record Record(
            String recordId,
            LocalDate date,
            String indicator,
            BigDecimal value,
            String unit,
            BigDecimal referenceLow,
            BigDecimal referenceHigh,
            Boolean referenceRangeEvaluable
    ) {
    }
}
