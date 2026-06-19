package com.liu.eemrsagent.reporttrend;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class TrendAnalysisService {
    private final ReportTrendProperties properties;

    public TrendAnalysisService(ReportTrendProperties properties) {
        this.properties = properties;
    }

    public List<TrendItem> analyze(List<StructuredLabReport> reports) {
        Map<String, List<Point>> byCode = new LinkedHashMap<>();
        for (StructuredLabReport report : reports) {
            for (LabIndicatorItem item : report.items()) {
                if (item.standardCode() == null || item.value() == null || item.standardCode().startsWith("UNKNOWN_")) {
                    continue;
                }
                byCode.computeIfAbsent(item.standardCode(), ignored -> new ArrayList<>()).add(new Point(report.reportDate(), item));
            }
        }
        List<TrendItem> results = new ArrayList<>();
        for (Map.Entry<String, List<Point>> entry : byCode.entrySet()) {
            List<Point> points = entry.getValue().stream().sorted(Comparator.comparing(Point::date)).toList();
            if (points.isEmpty()) {
                continue;
            }
            Point latest = points.get(points.size() - 1);
            Point previous = points.size() > 1 ? points.get(points.size() - 2) : null;
            BigDecimal latestValue = latest.item().value();
            BigDecimal previousValue = previous == null ? null : previous.item().value();
            BigDecimal min = points.stream().map(point -> point.item().value()).min(BigDecimal::compareTo).orElse(latestValue);
            BigDecimal max = points.stream().map(point -> point.item().value()).max(BigDecimal::compareTo).orElse(latestValue);
            BigDecimal changeAbs = previousValue == null ? null : latestValue.subtract(previousValue);
            BigDecimal changePercent = percent(changeAbs, previousValue);
            AbnormalSpan abnormalSpan = abnormalSpan(points);
            results.add(new TrendItem(
                    latest.item().standardCode(),
                    latest.item().standardName(),
                    latestValue,
                    previousValue,
                    min,
                    max,
                    changeAbs,
                    changePercent,
                    direction(points, changePercent),
                    latest.item().abnormalFlag(),
                    abnormalSpan.count(),
                    abnormalSpan.consecutive(),
                    abnormalSpan.first(),
                    abnormalSpan.latest()
            ));
        }
        return results;
    }

    private TrendDirection direction(List<Point> points, BigDecimal changePercent) {
        if (points.size() < 2) {
            return TrendDirection.INSUFFICIENT_DATA;
        }
        if (changePercent != null && changePercent.abs().compareTo(BigDecimal.valueOf(properties.getStableChangeThresholdPercent())) < 0) {
            return TrendDirection.STABLE;
        }
        if (points.size() >= 3) {
            BigDecimal a = points.get(points.size() - 3).item().value();
            BigDecimal b = points.get(points.size() - 2).item().value();
            BigDecimal c = points.get(points.size() - 1).item().value();
            if (a.compareTo(b) < 0 && b.compareTo(c) < 0) {
                return TrendDirection.INCREASING;
            }
            if (a.compareTo(b) > 0 && b.compareTo(c) > 0) {
                return TrendDirection.DECREASING;
            }
        }
        if (changePercent != null && changePercent.compareTo(BigDecimal.ZERO) > 0) {
            return TrendDirection.INCREASING;
        }
        if (changePercent != null && changePercent.compareTo(BigDecimal.ZERO) < 0) {
            return TrendDirection.DECREASING;
        }
        return TrendDirection.FLUCTUATING;
    }

    private BigDecimal percent(BigDecimal changeAbs, BigDecimal previous) {
        if (changeAbs == null || previous == null || BigDecimal.ZERO.compareTo(previous) == 0) {
            return null;
        }
        return changeAbs.multiply(BigDecimal.valueOf(100)).divide(previous.abs(), 2, RoundingMode.HALF_UP);
    }

    private AbnormalSpan abnormalSpan(List<Point> points) {
        int count = 0;
        int consecutive = 0;
        LocalDate first = null;
        LocalDate latest = null;
        for (Point point : points) {
            boolean abnormal = point.item().abnormalFlag() == AbnormalFlag.HIGH || point.item().abnormalFlag() == AbnormalFlag.LOW;
            if (abnormal) {
                count++;
                consecutive++;
                if (first == null) {
                    first = point.date();
                }
                latest = point.date();
            } else {
                consecutive = 0;
            }
        }
        return new AbnormalSpan(count, consecutive, first, latest);
    }

    private record Point(LocalDate date, LabIndicatorItem item) {
    }

    private record AbnormalSpan(int count, int consecutive, LocalDate first, LocalDate latest) {
    }
}
