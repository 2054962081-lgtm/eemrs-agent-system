package com.liu.eemrsagent.eval;

import java.util.ArrayList;
import java.util.List;

public class EvalActualResult {
    private String runId;
    private String actualDepartment;
    private boolean needFollowUp;
    private boolean toolCall;
    private boolean jsonParseFailed;
    private boolean fallbackParseUsed;
    private boolean missingFields;
    private boolean traceComplete;
    private long latencyMs;
    private int totalTokens;
    private List<String> matchedMustAsk = new ArrayList<>();
    private List<String> matchedRedFlags = new ArrayList<>();

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public String getActualDepartment() {
        return actualDepartment;
    }

    public void setActualDepartment(String actualDepartment) {
        this.actualDepartment = actualDepartment;
    }

    public boolean isNeedFollowUp() {
        return needFollowUp;
    }

    public void setNeedFollowUp(boolean needFollowUp) {
        this.needFollowUp = needFollowUp;
    }

    public boolean isToolCall() {
        return toolCall;
    }

    public void setToolCall(boolean toolCall) {
        this.toolCall = toolCall;
    }

    public boolean isJsonParseFailed() {
        return jsonParseFailed;
    }

    public void setJsonParseFailed(boolean jsonParseFailed) {
        this.jsonParseFailed = jsonParseFailed;
    }

    public boolean isFallbackParseUsed() {
        return fallbackParseUsed;
    }

    public void setFallbackParseUsed(boolean fallbackParseUsed) {
        this.fallbackParseUsed = fallbackParseUsed;
    }

    public boolean isMissingFields() {
        return missingFields;
    }

    public void setMissingFields(boolean missingFields) {
        this.missingFields = missingFields;
    }

    public boolean isTraceComplete() {
        return traceComplete;
    }

    public void setTraceComplete(boolean traceComplete) {
        this.traceComplete = traceComplete;
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

    public List<String> getMatchedMustAsk() {
        return matchedMustAsk;
    }

    public void setMatchedMustAsk(List<String> matchedMustAsk) {
        this.matchedMustAsk = matchedMustAsk == null ? new ArrayList<>() : matchedMustAsk;
    }

    public List<String> getMatchedRedFlags() {
        return matchedRedFlags;
    }

    public void setMatchedRedFlags(List<String> matchedRedFlags) {
        this.matchedRedFlags = matchedRedFlags == null ? new ArrayList<>() : matchedRedFlags;
    }
}
