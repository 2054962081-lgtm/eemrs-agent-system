package com.liu.eemrsagent.eval;

import java.util.LinkedHashMap;
import java.util.Map;

public class ReportTrendTraceEvidence {
    private String analysisId;
    private String traceRunId;
    private int reportCount;
    private int indicatorCount;
    private int abnormalCount;
    private int trendItemCount;
    private boolean contextAvailable;
    private String contextUsed;
    private int symptomTagCount;
    private int chronicDiseaseTagCount;
    private String recommendedDepartment;
    private String payloadHash;
    private String responseHash;
    private String modelName;
    private boolean cloudResponseValid;
    private String errorCode;
    private Map<String, String> stepStatusMap = new LinkedHashMap<>();
    private boolean sequenceDuplicate;
    private long latencyMs;
    private int totalTokens;
    private String privacyGuardStatus = "UNKNOWN";

    public String getAnalysisId() { return analysisId; }
    public void setAnalysisId(String analysisId) { this.analysisId = analysisId; }
    public String getTraceRunId() { return traceRunId; }
    public void setTraceRunId(String traceRunId) { this.traceRunId = traceRunId; }
    public int getReportCount() { return reportCount; }
    public void setReportCount(int reportCount) { this.reportCount = reportCount; }
    public int getIndicatorCount() { return indicatorCount; }
    public void setIndicatorCount(int indicatorCount) { this.indicatorCount = indicatorCount; }
    public int getAbnormalCount() { return abnormalCount; }
    public void setAbnormalCount(int abnormalCount) { this.abnormalCount = abnormalCount; }
    public int getTrendItemCount() { return trendItemCount; }
    public void setTrendItemCount(int trendItemCount) { this.trendItemCount = trendItemCount; }
    public boolean isContextAvailable() { return contextAvailable; }
    public void setContextAvailable(boolean contextAvailable) { this.contextAvailable = contextAvailable; }
    public String getContextUsed() { return contextUsed; }
    public void setContextUsed(String contextUsed) { this.contextUsed = contextUsed; }
    public int getSymptomTagCount() { return symptomTagCount; }
    public void setSymptomTagCount(int symptomTagCount) { this.symptomTagCount = symptomTagCount; }
    public int getChronicDiseaseTagCount() { return chronicDiseaseTagCount; }
    public void setChronicDiseaseTagCount(int chronicDiseaseTagCount) { this.chronicDiseaseTagCount = chronicDiseaseTagCount; }
    public String getRecommendedDepartment() { return recommendedDepartment; }
    public void setRecommendedDepartment(String recommendedDepartment) { this.recommendedDepartment = recommendedDepartment; }
    public String getPayloadHash() { return payloadHash; }
    public void setPayloadHash(String payloadHash) { this.payloadHash = payloadHash; }
    public String getResponseHash() { return responseHash; }
    public void setResponseHash(String responseHash) { this.responseHash = responseHash; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public boolean isCloudResponseValid() { return cloudResponseValid; }
    public void setCloudResponseValid(boolean cloudResponseValid) { this.cloudResponseValid = cloudResponseValid; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public Map<String, String> getStepStatusMap() { return stepStatusMap; }
    public void setStepStatusMap(Map<String, String> stepStatusMap) { this.stepStatusMap = stepStatusMap; }
    public boolean isSequenceDuplicate() { return sequenceDuplicate; }
    public void setSequenceDuplicate(boolean sequenceDuplicate) { this.sequenceDuplicate = sequenceDuplicate; }
    public long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(long latencyMs) { this.latencyMs = latencyMs; }
    public int getTotalTokens() { return totalTokens; }
    public void setTotalTokens(int totalTokens) { this.totalTokens = totalTokens; }
    public String getPrivacyGuardStatus() { return privacyGuardStatus; }
    public void setPrivacyGuardStatus(String privacyGuardStatus) { this.privacyGuardStatus = privacyGuardStatus; }
}
