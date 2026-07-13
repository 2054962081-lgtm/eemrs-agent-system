package com.liu.eemrsagent.reporttrend;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TrendItem(
        String code,
        String name,
        BigDecimal latestValue,
        BigDecimal previousValue,
        BigDecimal minValue,
        BigDecimal maxValue,
        BigDecimal changeAbsolute,
        BigDecimal changePercent,
        TrendDirection trendDirection,
        AbnormalFlag latestAbnormalFlag,
        int abnormalCount,
        int consecutiveAbnormalCount,
        LocalDate firstAbnormalDate,
        LocalDate latestAbnormalDate
) {
}
