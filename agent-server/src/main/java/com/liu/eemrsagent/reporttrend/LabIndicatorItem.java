package com.liu.eemrsagent.reporttrend;

import java.math.BigDecimal;

public record LabIndicatorItem(
        String rawName,
        String standardCode,
        String standardName,
        BigDecimal value,
        String unit,
        BigDecimal referenceLow,
        BigDecimal referenceHigh,
        AbnormalFlag abnormalFlag
) {
}
