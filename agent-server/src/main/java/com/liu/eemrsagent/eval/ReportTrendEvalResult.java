package com.liu.eemrsagent.eval;

import java.time.LocalDateTime;

public class ReportTrendEvalResult {
    private String caseId;
    private String source;
    private String scenario;
    private String category;
    private String runId;
    private String analysisId;
    private String status = "FAILED";
    private Boolean abnormalDetectionCorrect;
    private Boolean trendDirectionCorrect;
    private Boolean contextLinkCorrect;
    private Boolean suggestedDepartmentCorrect;
    private Boolean privacyPass;
    private Boolean cloudResponseValid;
    private Boolean doctorSummaryPresent;
    private Boolean patientExplanationPresent;
    private Boolean contextualInterpretationPresent;
    private Boolean traceComplete;
    private ReportTrendFailureStage failureStage = ReportTrendFailureStage.UNKNOWN;
    private String failureReason;
    private String expectedAbnormalCodes;
    private String actualAbnormalCodes;
    private String expectedTrends;
    private String actualTrends;
    private String expectedContextLinks;
    private String actualContextLinks;
    private long latencyMs;
    private int totalTokens;
    private LocalDateTime createdAt = LocalDateTime.now();

    public String getCaseId() { return caseId; }
    public void setCaseId(String caseId) { this.caseId = caseId; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getScenario() { return scenario; }
    public void setScenario(String scenario) { this.scenario = scenario; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }
    public String getAnalysisId() { return analysisId; }
    public void setAnalysisId(String analysisId) { this.analysisId = analysisId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Boolean getAbnormalDetectionCorrect() { return abnormalDetectionCorrect; }
    public void setAbnormalDetectionCorrect(Boolean abnormalDetectionCorrect) { this.abnormalDetectionCorrect = abnormalDetectionCorrect; }
    public Boolean getTrendDirectionCorrect() { return trendDirectionCorrect; }
    public void setTrendDirectionCorrect(Boolean trendDirectionCorrect) { this.trendDirectionCorrect = trendDirectionCorrect; }
    public Boolean getContextLinkCorrect() { return contextLinkCorrect; }
    public void setContextLinkCorrect(Boolean contextLinkCorrect) { this.contextLinkCorrect = contextLinkCorrect; }
    public Boolean getSuggestedDepartmentCorrect() { return suggestedDepartmentCorrect; }
    public void setSuggestedDepartmentCorrect(Boolean suggestedDepartmentCorrect) { this.suggestedDepartmentCorrect = suggestedDepartmentCorrect; }
    public Boolean getPrivacyPass() { return privacyPass; }
    public void setPrivacyPass(Boolean privacyPass) { this.privacyPass = privacyPass; }
    public Boolean getCloudResponseValid() { return cloudResponseValid; }
    public void setCloudResponseValid(Boolean cloudResponseValid) { this.cloudResponseValid = cloudResponseValid; }
    public Boolean getDoctorSummaryPresent() { return doctorSummaryPresent; }
    public void setDoctorSummaryPresent(Boolean doctorSummaryPresent) { this.doctorSummaryPresent = doctorSummaryPresent; }
    public Boolean getPatientExplanationPresent() { return patientExplanationPresent; }
    public void setPatientExplanationPresent(Boolean patientExplanationPresent) { this.patientExplanationPresent = patientExplanationPresent; }
    public Boolean getContextualInterpretationPresent() { return contextualInterpretationPresent; }
    public void setContextualInterpretationPresent(Boolean contextualInterpretationPresent) { this.contextualInterpretationPresent = contextualInterpretationPresent; }
    public Boolean getTraceComplete() { return traceComplete; }
    public void setTraceComplete(Boolean traceComplete) { this.traceComplete = traceComplete; }
    public ReportTrendFailureStage getFailureStage() { return failureStage; }
    public void setFailureStage(ReportTrendFailureStage failureStage) { this.failureStage = failureStage; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public String getExpectedAbnormalCodes() { return expectedAbnormalCodes; }
    public void setExpectedAbnormalCodes(String expectedAbnormalCodes) { this.expectedAbnormalCodes = expectedAbnormalCodes; }
    public String getActualAbnormalCodes() { return actualAbnormalCodes; }
    public void setActualAbnormalCodes(String actualAbnormalCodes) { this.actualAbnormalCodes = actualAbnormalCodes; }
    public String getExpectedTrends() { return expectedTrends; }
    public void setExpectedTrends(String expectedTrends) { this.expectedTrends = expectedTrends; }
    public String getActualTrends() { return actualTrends; }
    public void setActualTrends(String actualTrends) { this.actualTrends = actualTrends; }
    public String getExpectedContextLinks() { return expectedContextLinks; }
    public void setExpectedContextLinks(String expectedContextLinks) { this.expectedContextLinks = expectedContextLinks; }
    public String getActualContextLinks() { return actualContextLinks; }
    public void setActualContextLinks(String actualContextLinks) { this.actualContextLinks = actualContextLinks; }
    public long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(long latencyMs) { this.latencyMs = latencyMs; }
    public int getTotalTokens() { return totalTokens; }
    public void setTotalTokens(int totalTokens) { this.totalTokens = totalTokens; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
