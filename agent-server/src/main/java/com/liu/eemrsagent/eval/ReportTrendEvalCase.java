package com.liu.eemrsagent.eval;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ReportTrendEvalCase {
    @JsonAlias("caseId")
    private String caseId;
    private String source;
    private String scenario;
    private String category;
    @JsonAlias("report_type")
    private String reportType;
    private Map<String, Object> input;
    private Expected expected = new Expected();
    private List<String> tags = new ArrayList<>();

    public String getCaseId() { return caseId; }
    public void setCaseId(String caseId) { this.caseId = caseId; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getScenario() { return scenario; }
    public void setScenario(String scenario) { this.scenario = scenario; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getReportType() { return reportType; }
    public void setReportType(String reportType) { this.reportType = reportType; }
    public Map<String, Object> getInput() { return input; }
    public void setInput(Map<String, Object> input) { this.input = input; }
    public Expected getExpected() { return expected == null ? new Expected() : expected; }
    public void setExpected(Expected expected) { this.expected = expected; }
    public List<String> getTags() { return tags == null ? List.of() : tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public static class Expected {
        @JsonAlias("expected_abnormal_items")
        private List<ExpectedAbnormalItem> expectedAbnormalItems = new ArrayList<>();
        @JsonAlias("expected_trends")
        private List<ExpectedTrend> expectedTrends = new ArrayList<>();
        @JsonAlias("expected_context_links")
        private List<ExpectedContextLink> expectedContextLinks = new ArrayList<>();
        @JsonAlias("expected_suggested_department")
        private String expectedSuggestedDepartment;
        @JsonAlias("doctor_summary_required")
        private boolean doctorSummaryRequired;
        @JsonAlias("patient_explanation_required")
        private boolean patientExplanationRequired;
        @JsonAlias("contextual_interpretation_required")
        private boolean contextualInterpretationRequired;
        @JsonAlias("privacy_pass_required")
        private boolean privacyPassRequired;
        @JsonAlias("cloud_response_valid_required")
        private boolean cloudResponseValidRequired;
        @JsonAlias("trace_complete_required")
        private boolean traceCompleteRequired;
        @JsonAlias("forbidden_behaviors")
        private List<String> forbiddenBehaviors = new ArrayList<>();

        public List<ExpectedAbnormalItem> getExpectedAbnormalItems() { return expectedAbnormalItems == null ? List.of() : expectedAbnormalItems; }
        public void setExpectedAbnormalItems(List<ExpectedAbnormalItem> expectedAbnormalItems) { this.expectedAbnormalItems = expectedAbnormalItems; }
        public List<ExpectedTrend> getExpectedTrends() { return expectedTrends == null ? List.of() : expectedTrends; }
        public void setExpectedTrends(List<ExpectedTrend> expectedTrends) { this.expectedTrends = expectedTrends; }
        public List<ExpectedContextLink> getExpectedContextLinks() { return expectedContextLinks == null ? List.of() : expectedContextLinks; }
        public void setExpectedContextLinks(List<ExpectedContextLink> expectedContextLinks) { this.expectedContextLinks = expectedContextLinks; }
        public String getExpectedSuggestedDepartment() { return expectedSuggestedDepartment; }
        public void setExpectedSuggestedDepartment(String expectedSuggestedDepartment) { this.expectedSuggestedDepartment = expectedSuggestedDepartment; }
        public boolean isDoctorSummaryRequired() { return doctorSummaryRequired; }
        public void setDoctorSummaryRequired(boolean doctorSummaryRequired) { this.doctorSummaryRequired = doctorSummaryRequired; }
        public boolean isPatientExplanationRequired() { return patientExplanationRequired; }
        public void setPatientExplanationRequired(boolean patientExplanationRequired) { this.patientExplanationRequired = patientExplanationRequired; }
        public boolean isContextualInterpretationRequired() { return contextualInterpretationRequired; }
        public void setContextualInterpretationRequired(boolean contextualInterpretationRequired) { this.contextualInterpretationRequired = contextualInterpretationRequired; }
        public boolean isPrivacyPassRequired() { return privacyPassRequired; }
        public void setPrivacyPassRequired(boolean privacyPassRequired) { this.privacyPassRequired = privacyPassRequired; }
        public boolean isCloudResponseValidRequired() { return cloudResponseValidRequired; }
        public void setCloudResponseValidRequired(boolean cloudResponseValidRequired) { this.cloudResponseValidRequired = cloudResponseValidRequired; }
        public boolean isTraceCompleteRequired() { return traceCompleteRequired; }
        public void setTraceCompleteRequired(boolean traceCompleteRequired) { this.traceCompleteRequired = traceCompleteRequired; }
        public List<String> getForbiddenBehaviors() { return forbiddenBehaviors == null ? List.of() : forbiddenBehaviors; }
        public void setForbiddenBehaviors(List<String> forbiddenBehaviors) { this.forbiddenBehaviors = forbiddenBehaviors; }
    }

    public record ExpectedAbnormalItem(String code, String name, @JsonAlias("expected_flag") String expectedFlag) {}
    public record ExpectedTrend(String code, @JsonAlias("expected_direction") String expectedDirection) {}
    public record ExpectedContextLink(String type, List<String> symptoms, List<String> indicators) {}
}
