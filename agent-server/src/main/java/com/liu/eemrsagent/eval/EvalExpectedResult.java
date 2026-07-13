package com.liu.eemrsagent.eval;

import java.util.ArrayList;
import java.util.List;

public class EvalExpectedResult {
    private List<String> expectedDepartments = new ArrayList<>();
    private String primaryDepartment;
    private List<EvalKeyPoint> expectedMustAsk = new ArrayList<>();
    private List<EvalKeyPoint> expectedRedFlags = new ArrayList<>();
    private boolean shouldFollowUp;
    private boolean shouldRecommendRegistration;
    private Boolean expectedToolCall;
    private String riskLevel = "medium";
    private List<String> forbiddenBehaviors = new ArrayList<>();

    public List<String> getExpectedDepartments() {
        return expectedDepartments;
    }

    public void setExpectedDepartments(List<String> expectedDepartments) {
        this.expectedDepartments = expectedDepartments == null ? new ArrayList<>() : expectedDepartments;
    }

    public String getPrimaryDepartment() {
        return primaryDepartment;
    }

    public void setPrimaryDepartment(String primaryDepartment) {
        this.primaryDepartment = primaryDepartment;
    }

    public List<EvalKeyPoint> getExpectedMustAsk() {
        return expectedMustAsk;
    }

    public void setExpectedMustAsk(List<EvalKeyPoint> expectedMustAsk) {
        this.expectedMustAsk = expectedMustAsk == null ? new ArrayList<>() : expectedMustAsk;
    }

    public List<EvalKeyPoint> getExpectedRedFlags() {
        return expectedRedFlags;
    }

    public void setExpectedRedFlags(List<EvalKeyPoint> expectedRedFlags) {
        this.expectedRedFlags = expectedRedFlags == null ? new ArrayList<>() : expectedRedFlags;
    }

    public boolean isShouldFollowUp() {
        return shouldFollowUp;
    }

    public void setShouldFollowUp(boolean shouldFollowUp) {
        this.shouldFollowUp = shouldFollowUp;
    }

    public boolean isShouldRecommendRegistration() {
        return shouldRecommendRegistration;
    }

    public void setShouldRecommendRegistration(boolean shouldRecommendRegistration) {
        this.shouldRecommendRegistration = shouldRecommendRegistration;
    }

    public Boolean getExpectedToolCall() {
        return expectedToolCall;
    }

    public void setExpectedToolCall(Boolean expectedToolCall) {
        this.expectedToolCall = expectedToolCall;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public List<String> getForbiddenBehaviors() {
        return forbiddenBehaviors;
    }

    public void setForbiddenBehaviors(List<String> forbiddenBehaviors) {
        this.forbiddenBehaviors = forbiddenBehaviors == null ? new ArrayList<>() : forbiddenBehaviors;
    }
}
