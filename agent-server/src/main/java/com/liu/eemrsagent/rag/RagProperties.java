package com.liu.eemrsagent.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    private boolean enabled = true;
    private String serviceUrl = "http://localhost:18080";
    private String retrievePath = "/rag/retrieve";
    private int timeoutMs = 3000;
    private int topK = 8;
    private int maxContextChars = 3000;
    private List<String> includeDocTypes = new ArrayList<>(List.of(
            "red_flag",
            "symptom_inquiry",
            "special_population",
            "department_triage",
            "medical_record_template"
    ));
    private boolean failOpen = true;
    private boolean debugLog = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getServiceUrl() {
        return serviceUrl;
    }

    public void setServiceUrl(String serviceUrl) {
        this.serviceUrl = serviceUrl;
    }

    public String getRetrievePath() {
        return retrievePath;
    }

    public void setRetrievePath(String retrievePath) {
        this.retrievePath = retrievePath;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public int getMaxContextChars() {
        return maxContextChars;
    }

    public void setMaxContextChars(int maxContextChars) {
        this.maxContextChars = maxContextChars;
    }

    public List<String> getIncludeDocTypes() {
        return includeDocTypes;
    }

    public void setIncludeDocTypes(List<String> includeDocTypes) {
        this.includeDocTypes = includeDocTypes == null ? new ArrayList<>() : includeDocTypes;
    }

    public boolean isFailOpen() {
        return failOpen;
    }

    public void setFailOpen(boolean failOpen) {
        this.failOpen = failOpen;
    }

    public boolean isDebugLog() {
        return debugLog;
    }

    public void setDebugLog(boolean debugLog) {
        this.debugLog = debugLog;
    }
}
