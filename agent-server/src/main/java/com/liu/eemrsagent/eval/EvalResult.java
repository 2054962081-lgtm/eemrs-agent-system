package com.liu.eemrsagent.eval;

import java.time.LocalDateTime;

public class EvalResult {
    private String caseId;
    private String source;
    private String scenario;
    private String category;
    private String riskLevel;
    private String runId;
    private String status = "PASSED";
    private String actualDepartment;
    private String expectedDepartments;
    private Boolean departmentCorrect;
    private Boolean primaryDepartmentCorrect;
    private double mustAskCoverage;
    private double redFlagHitRate;
    private boolean followUpExpected;
    private boolean followUpActual;
    private boolean followUpCorrect;
    private String toolCallExpected;
    private String toolCallActual;
    private String toolCallCorrect;
    private FailureStage failureStage = FailureStage.NONE;
    private String failureReason = "";
    private long latencyMs;
    private int totalTokens;
    private boolean traceComplete;
    private LocalDateTime createdAt = LocalDateTime.now();

    public String getCaseId() {
        return caseId;
    }

    public void setCaseId(String caseId) {
        this.caseId = caseId;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getScenario() {
        return scenario;
    }

    public void setScenario(String scenario) {
        this.scenario = scenario;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getActualDepartment() {
        return actualDepartment;
    }

    public void setActualDepartment(String actualDepartment) {
        this.actualDepartment = actualDepartment;
    }

    public String getExpectedDepartments() {
        return expectedDepartments;
    }

    public void setExpectedDepartments(String expectedDepartments) {
        this.expectedDepartments = expectedDepartments;
    }

    public Boolean getDepartmentCorrect() {
        return departmentCorrect;
    }

    public void setDepartmentCorrect(Boolean departmentCorrect) {
        this.departmentCorrect = departmentCorrect;
    }

    public Boolean getPrimaryDepartmentCorrect() {
        return primaryDepartmentCorrect;
    }

    public void setPrimaryDepartmentCorrect(Boolean primaryDepartmentCorrect) {
        this.primaryDepartmentCorrect = primaryDepartmentCorrect;
    }

    public double getMustAskCoverage() {
        return mustAskCoverage;
    }

    public void setMustAskCoverage(double mustAskCoverage) {
        this.mustAskCoverage = mustAskCoverage;
    }

    public double getRedFlagHitRate() {
        return redFlagHitRate;
    }

    public void setRedFlagHitRate(double redFlagHitRate) {
        this.redFlagHitRate = redFlagHitRate;
    }

    public boolean isFollowUpExpected() {
        return followUpExpected;
    }

    public void setFollowUpExpected(boolean followUpExpected) {
        this.followUpExpected = followUpExpected;
    }

    public boolean isFollowUpActual() {
        return followUpActual;
    }

    public void setFollowUpActual(boolean followUpActual) {
        this.followUpActual = followUpActual;
    }

    public boolean isFollowUpCorrect() {
        return followUpCorrect;
    }

    public void setFollowUpCorrect(boolean followUpCorrect) {
        this.followUpCorrect = followUpCorrect;
    }

    public String getToolCallExpected() {
        return toolCallExpected;
    }

    public void setToolCallExpected(String toolCallExpected) {
        this.toolCallExpected = toolCallExpected;
    }

    public String getToolCallActual() {
        return toolCallActual;
    }

    public void setToolCallActual(String toolCallActual) {
        this.toolCallActual = toolCallActual;
    }

    public String getToolCallCorrect() {
        return toolCallCorrect;
    }

    public void setToolCallCorrect(String toolCallCorrect) {
        this.toolCallCorrect = toolCallCorrect;
    }

    public FailureStage getFailureStage() {
        return failureStage;
    }

    public void setFailureStage(FailureStage failureStage) {
        this.failureStage = failureStage;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public int getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(int totalTokens) {
        this.totalTokens = totalTokens;
    }

    public boolean isTraceComplete() {
        return traceComplete;
    }

    public void setTraceComplete(boolean traceComplete) {
        this.traceComplete = traceComplete;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
