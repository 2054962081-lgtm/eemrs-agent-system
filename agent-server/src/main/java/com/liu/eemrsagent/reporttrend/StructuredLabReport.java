package com.liu.eemrsagent.reporttrend;

import java.time.LocalDate;
import java.util.List;

public record StructuredLabReport(
        String reportIdHash,
        LocalDate reportDate,
        String reportType,
        List<LabIndicatorItem> items
) {
}
