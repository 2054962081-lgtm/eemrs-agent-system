package com.liu.eemrsagent.eval;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ReportTrendTraceBasedEvaluator {
    private static final Pattern ID_CARD = Pattern.compile("\\b\\d{17}[0-9Xx]\\b");
    private static final Pattern MOBILE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");

    public ReportTrendEvalResult evaluate(ReportTrendEvalCase evalCase, ReportTrendActualResult actual, ReportTrendTraceEvidence evidence) {
        ReportTrendEvalResult result = new ReportTrendEvalResult();
        result.setCaseId(evalCase.getCaseId());
        result.setSource(evalCase.getSource());
        result.setScenario(evalCase.getScenario());
        result.setCategory(evalCase.getCategory());
        result.setRunId(actual.getTraceRunId() == null ? evidence.getTraceRunId() : actual.getTraceRunId());
        result.setAnalysisId(actual.getAnalysisId());
        result.setAbnormalDetectionCorrect(metricAbnormal(evalCase, actual));
        result.setTrendDirectionCorrect(metricTrend(evalCase, actual));
        result.setContextLinkCorrect(metricContextLink(evalCase, actual));
        result.setSuggestedDepartmentCorrect(metricDepartment(evalCase, actual));
        result.setPrivacyPass(metricPrivacy(actual, evidence));
        result.setCloudResponseValid(metricCloudResponse(evalCase, actual, evidence));
        result.setDoctorSummaryPresent(!blank(actual.getDoctorSummary()));
        result.setPatientExplanationPresent(!blank(actual.getPatientExplanation()));
        result.setContextualInterpretationPresent(!blank(actual.getContextualInterpretation()));
        result.setTraceComplete(traceComplete(evidence));
        result.setExpectedAbnormalCodes(evalCase.getExpected().getExpectedAbnormalItems().stream().map(ReportTrendEvalCase.ExpectedAbnormalItem::code).collect(Collectors.joining("|")));
        result.setActualAbnormalCodes(String.join("|", actual.abnormalFlagsByCode().keySet()));
        result.setExpectedTrends(evalCase.getExpected().getExpectedTrends().stream().map(item -> item.code() + ":" + item.expectedDirection()).collect(Collectors.joining("|")));
        result.setActualTrends(actual.trendDirectionsByCode().entrySet().stream().map(e -> e.getKey() + ":" + e.getValue()).collect(Collectors.joining("|")));
        result.setExpectedContextLinks(evalCase.getExpected().getExpectedContextLinks().toString());
        result.setActualContextLinks(actual.getContextLinks().toString());
        result.setLatencyMs(evidence.getLatencyMs());
        result.setTotalTokens(evidence.getTotalTokens());
        return result;
    }

    private Boolean metricAbnormal(ReportTrendEvalCase evalCase, ReportTrendActualResult actual) {
        List<ReportTrendEvalCase.ExpectedAbnormalItem> expected = evalCase.getExpected().getExpectedAbnormalItems();
        if (expected.isEmpty()) return null;
        Map<String, String> actualFlags = actual.abnormalFlagsByCode();
        return expected.stream().allMatch(item -> item.expectedFlag().equals(actualFlags.get(item.code())));
    }

    private Boolean metricTrend(ReportTrendEvalCase evalCase, ReportTrendActualResult actual) {
        List<ReportTrendEvalCase.ExpectedTrend> expected = evalCase.getExpected().getExpectedTrends();
        if (expected.isEmpty()) return null;
        Map<String, String> actualDirections = actual.trendDirectionsByCode();
        return expected.stream().allMatch(item -> item.expectedDirection().equals(actualDirections.get(item.code())));
    }

    private Boolean metricContextLink(ReportTrendEvalCase evalCase, ReportTrendActualResult actual) {
        List<ReportTrendEvalCase.ExpectedContextLink> expected = evalCase.getExpected().getExpectedContextLinks();
        if (expected.isEmpty()) return null;
        String text = (actual.getContextLinks() + " " + actual.getContextualInterpretation()).toLowerCase();
        return expected.stream().allMatch(link -> anyContains(text, link.symptoms()) && anyContains(text, link.indicators()));
    }

    private Boolean metricDepartment(ReportTrendEvalCase evalCase, ReportTrendActualResult actual) {
        String expected = evalCase.getExpected().getExpectedSuggestedDepartment();
        if (blank(expected)) return null;
        return expected.equals(actual.getSuggestedDepartment());
    }

    private Boolean metricPrivacy(ReportTrendActualResult actual, ReportTrendTraceEvidence evidence) {
        String text = actual.getSanitizedText() + " " + evidence.getPayloadHash() + " " + evidence.getResponseHash();
        String lower = text.toLowerCase();
        return !ID_CARD.matcher(text).find()
                && !MOBILE.matcher(text).find()
                && !lower.contains("patientid")
                && !lower.contains("doctorid")
                && !lower.contains("visitid")
                && !lower.contains("authorization")
                && !lower.contains("cookie")
                && !lower.contains("apikey")
                && !lower.contains("ciphertext")
                && !lower.contains("rawreporttext");
    }

    private Boolean metricCloudResponse(ReportTrendEvalCase evalCase, ReportTrendActualResult actual, ReportTrendTraceEvidence evidence) {
        boolean base = !blank(actual.getDoctorSummary())
                && !blank(actual.getPatientExplanation())
                && actual.getFollowUpQuestions() != null
                && !blank(actual.getSuggestedDepartment())
                && !blank(actual.getSuggestedAction());
        if (evalCase.getExpected().isContextualInterpretationRequired()) {
            base = base && !blank(actual.getContextualInterpretation()) && actual.getContextLinks() != null;
        }
        return base && evidence.isCloudResponseValid();
    }

    private boolean traceComplete(ReportTrendTraceEvidence evidence) {
        return evidence.getTraceRunId() != null
                && !evidence.isSequenceDuplicate()
                && evidence.getStepStatusMap().keySet().containsAll(ReportTrendTraceParser.requiredSteps());
    }

    private boolean anyContains(String text, List<String> values) {
        return values != null && values.stream().anyMatch(value -> value != null && text.contains(value.toLowerCase()));
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
