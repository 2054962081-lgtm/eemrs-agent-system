package com.liu.eemrsagent.trace;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent.trace")
public class TraceProperties {

    private boolean enabled = true;
    private boolean persistenceEnabled = true;
    private boolean queryApiEnabled = false;
    private boolean payloadEnabled = false;
    private TraceContentCaptureLevel contentCaptureLevel = TraceContentCaptureLevel.METADATA_ONLY;
    private int payloadMaxLength = 4000;
    private int summaryMaxLength = 1000;
    private String userHashSalt = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isPersistenceEnabled() {
        return persistenceEnabled;
    }

    public void setPersistenceEnabled(boolean persistenceEnabled) {
        this.persistenceEnabled = persistenceEnabled;
    }

    public boolean isQueryApiEnabled() {
        return queryApiEnabled;
    }

    public void setQueryApiEnabled(boolean queryApiEnabled) {
        this.queryApiEnabled = queryApiEnabled;
    }

    public boolean isPayloadEnabled() {
        return payloadEnabled;
    }

    public void setPayloadEnabled(boolean payloadEnabled) {
        this.payloadEnabled = payloadEnabled;
    }

    public TraceContentCaptureLevel getContentCaptureLevel() {
        return contentCaptureLevel;
    }

    public void setContentCaptureLevel(TraceContentCaptureLevel contentCaptureLevel) {
        this.contentCaptureLevel = contentCaptureLevel == null ? TraceContentCaptureLevel.METADATA_ONLY : contentCaptureLevel;
    }

    public int getPayloadMaxLength() {
        return payloadMaxLength;
    }

    public void setPayloadMaxLength(int payloadMaxLength) {
        this.payloadMaxLength = payloadMaxLength;
    }

    public int getSummaryMaxLength() {
        return summaryMaxLength;
    }

    public void setSummaryMaxLength(int summaryMaxLength) {
        this.summaryMaxLength = summaryMaxLength;
    }

    public String getUserHashSalt() {
        return userHashSalt;
    }

    public void setUserHashSalt(String userHashSalt) {
        this.userHashSalt = userHashSalt;
    }
}
