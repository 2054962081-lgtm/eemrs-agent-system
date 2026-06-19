package com.liu.eemrsagent.eval;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ReportTrendActualResult {
    private String analysisId;
    private String traceRunId;
    private String status;
    private String doctorSummary;
    private String patientExplanation;
    private String contextualInterpretation;
    private List<Map<String, Object>> contextLinks = new ArrayList<>();
    private List<Map<String, Object>> abnormalItems = new ArrayList<>();
    private List<Map<String, Object>> trendItems = new ArrayList<>();
    private List<String> followUpQuestions = new ArrayList<>();
    private String suggestedDepartment;
    private String suggestedAction;
    private String errorCode;
    private String sanitizedText = "";

    public String getAnalysisId() { return analysisId; }
    public void setAnalysisId(String analysisId) { this.analysisId = analysisId; }
    public String getTraceRunId() { return traceRunId; }
    public void setTraceRunId(String traceRunId) { this.traceRunId = traceRunId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDoctorSummary() { return doctorSummary; }
    public void setDoctorSummary(String doctorSummary) { this.doctorSummary = doctorSummary; }
    public String getPatientExplanation() { return patientExplanation; }
    public void setPatientExplanation(String patientExplanation) { this.patientExplanation = patientExplanation; }
    public String getContextualInterpretation() { return contextualInterpretation; }
    public void setContextualInterpretation(String contextualInterpretation) { this.contextualInterpretation = contextualInterpretation; }
    public List<Map<String, Object>> getContextLinks() { return contextLinks; }
    public void setContextLinks(List<Map<String, Object>> contextLinks) { this.contextLinks = contextLinks == null ? List.of() : contextLinks; }
    public List<Map<String, Object>> getAbnormalItems() { return abnormalItems; }
    public void setAbnormalItems(List<Map<String, Object>> abnormalItems) { this.abnormalItems = abnormalItems == null ? List.of() : abnormalItems; }
    public List<Map<String, Object>> getTrendItems() { return trendItems; }
    public void setTrendItems(List<Map<String, Object>> trendItems) { this.trendItems = trendItems == null ? List.of() : trendItems; }
    public List<String> getFollowUpQuestions() { return followUpQuestions; }
    public void setFollowUpQuestions(List<String> followUpQuestions) { this.followUpQuestions = followUpQuestions == null ? List.of() : followUpQuestions; }
    public String getSuggestedDepartment() { return suggestedDepartment; }
    public void setSuggestedDepartment(String suggestedDepartment) { this.suggestedDepartment = suggestedDepartment; }
    public String getSuggestedAction() { return suggestedAction; }
    public void setSuggestedAction(String suggestedAction) { this.suggestedAction = suggestedAction; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getSanitizedText() { return sanitizedText; }
    public void setSanitizedText(String sanitizedText) { this.sanitizedText = sanitizedText == null ? "" : sanitizedText; }

    public Map<String, String> abnormalFlagsByCode() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map<String, Object> item : abnormalItems) {
            String code = string(item, "standardCode", "code");
            String flag = string(item, "abnormalFlag", "expected_flag", "flag");
            if (code != null && flag != null) {
                out.put(code, flag);
            }
        }
        return out;
    }

    public Map<String, String> trendDirectionsByCode() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map<String, Object> item : trendItems) {
            String code = string(item, "code", "standardCode");
            String direction = string(item, "trendDirection", "trend", "direction");
            if (code != null && direction != null) {
                out.put(code, direction);
            }
        }
        return out;
    }

    private String string(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }
        return null;
    }
}
