package com.liu.eemrsagent.reporttrend;

import com.liu.eemrsagent.trace.TraceRedactor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class LocalReportStructuringService {
    private static final Pattern LINE_PATTERN = Pattern.compile(
            "^\\s*([^,:：，\\s]+(?:\\s*[^,:：，\\s]+){0,3})\\s*[:：,，\\s]\\s*(-?\\d+(?:\\.\\d+)?)\\s*([^\\s,，;；]*)\\s*(?:参考|ref|range)?\\s*([<>]?)\\s*(-?\\d+(?:\\.\\d+)?)?\\s*(?:[-~～至]\\s*(-?\\d+(?:\\.\\d+)?))?.*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern RANGE_PATTERN = Pattern.compile("(-?\\d+(?:\\.\\d+)?)\\s*[-~～至]\\s*(-?\\d+(?:\\.\\d+)?)");
    private static final Pattern LESS_THAN_PATTERN = Pattern.compile("<\\s*(-?\\d+(?:\\.\\d+)?)");
    private static final Pattern GREATER_THAN_PATTERN = Pattern.compile(">\\s*(-?\\d+(?:\\.\\d+)?)");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("-?\\d+(?:\\.\\d+)?");

    private final LabIndicatorDictionary dictionary;
    private final TraceRedactor traceRedactor;

    public LocalReportStructuringService(LabIndicatorDictionary dictionary, TraceRedactor traceRedactor) {
        this.dictionary = dictionary;
        this.traceRedactor = traceRedactor;
    }

    public StructuredLabReport structure(String reportId, LocalDate reportDate, String reportType, String redactedText) {
        List<LabIndicatorItem> items = new ArrayList<>();
        String[] lines = redactedText == null ? new String[0] : redactedText.split("\\R");
        for (String line : lines) {
            Matcher matcher = LINE_PATTERN.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            String rawName = dictionaryRawName(line, matcher.group(1).trim());
            BigDecimal value = firstNumberAfter(line, rawName, matcher.group(2));
            if (value == null) {
                continue;
            }
            LabIndicatorDictionary.IndicatorDefinition definition = dictionary.find(rawName).orElse(null);
            String code = definition == null ? "UNKNOWN_" + Math.abs(rawName.hashCode()) : definition.code();
            String name = definition == null ? rawName : definition.name();
            String unit = matcher.group(3) == null ? "" : matcher.group(3).trim();
            BigDecimal low = null;
            BigDecimal high = null;
            String sign = matcher.group(4);
            BigDecimal firstRef = decimal(matcher.group(5));
            BigDecimal secondRef = decimal(matcher.group(6));
            if ("<".equals(sign)) {
                high = firstRef;
            } else if (">".equals(sign)) {
                low = firstRef;
            } else if (secondRef != null) {
                low = firstRef;
                high = secondRef;
            }
            BigDecimal[] parsedRange = parseReferenceRange(line);
            if (parsedRange[0] != null || parsedRange[1] != null) {
                low = parsedRange[0];
                high = parsedRange[1];
            }
            items.add(new LabIndicatorItem(rawName, code, name, value, unit, low, high, abnormalFlag(value, low, high)));
        }
        if (items.isEmpty()) {
            throw new ReportTrendException(ReportTrendErrorCode.REPORT_PARSE_FAILED, "No structured lab indicator found");
        }
        return new StructuredLabReport(traceRedactor.stableHash(reportId), reportDate, reportType, items);
    }

    private BigDecimal decimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal[] parseReferenceRange(String line) {
        String scope = referenceScope(line);
        Matcher range = RANGE_PATTERN.matcher(scope);
        if (range.find()) {
            return new BigDecimal[]{decimal(range.group(1)), decimal(range.group(2))};
        }
        Matcher lessThan = LESS_THAN_PATTERN.matcher(scope);
        if (lessThan.find()) {
            return new BigDecimal[]{null, decimal(lessThan.group(1))};
        }
        Matcher greaterThan = GREATER_THAN_PATTERN.matcher(scope);
        if (greaterThan.find()) {
            return new BigDecimal[]{decimal(greaterThan.group(1)), null};
        }
        return new BigDecimal[]{null, null};
    }

    private String referenceScope(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        int index = lower.indexOf("参考");
        if (index < 0) {
            index = lower.indexOf("ref");
        }
        if (index < 0) {
            index = lower.indexOf("range");
        }
        return index < 0 ? "" : line.substring(index);
    }

    private String dictionaryRawName(String line, String fallback) {
        String normalizedLine = normalize(line);
        String best = null;
        int bestLength = -1;
        for (LabIndicatorDictionary.IndicatorDefinition definition : dictionary.all()) {
            if (startsWith(normalizedLine, definition.code()) && definition.code().length() > bestLength) {
                best = definition.code();
                bestLength = definition.code().length();
            }
            if (startsWith(normalizedLine, definition.name()) && definition.name().length() > bestLength) {
                best = definition.name();
                bestLength = definition.name().length();
            }
            for (String alias : definition.aliases()) {
                if (startsWith(normalizedLine, alias) && alias.length() > bestLength) {
                    best = alias;
                    bestLength = alias.length();
                }
            }
        }
        return best == null ? fallback : best;
    }

    private boolean startsWith(String normalizedLine, String candidate) {
        return candidate != null && normalizedLine.startsWith(normalize(candidate));
    }

    private BigDecimal firstNumberAfter(String line, String rawName, String fallback) {
        int index = rawName == null ? -1 : line.indexOf(rawName);
        String scope = index < 0 ? line : line.substring(index + rawName.length());
        Matcher matcher = NUMBER_PATTERN.matcher(scope);
        if (matcher.find()) {
            return decimal(matcher.group());
        }
        return decimal(fallback);
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").replace("-", "_").toUpperCase(Locale.ROOT);
    }

    private AbnormalFlag abnormalFlag(BigDecimal value, BigDecimal low, BigDecimal high) {
        if (value == null) {
            return AbnormalFlag.UNKNOWN;
        }
        if (low != null && value.compareTo(low) < 0) {
            return AbnormalFlag.LOW;
        }
        if (high != null && value.compareTo(high) > 0) {
            return AbnormalFlag.HIGH;
        }
        if (low == null && high == null) {
            return AbnormalFlag.UNKNOWN;
        }
        return AbnormalFlag.NORMAL;
    }
}
