package com.liu.eemrsagent.trace;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "agent.trace")
public class TraceProperties {

    private boolean enabled = true;
    private boolean persistenceEnabled = true;
    private boolean payloadEnabled = false;
    private int payloadMaxLength = 4000;
    private int summaryMaxLength = 1000;
    private boolean asyncEnabled = true;
    private int retentionDays = 30;
    private String userHashSalt = "";
    private final Cost cost = new Cost();

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

    public boolean isPayloadEnabled() {
        return payloadEnabled;
    }

    public void setPayloadEnabled(boolean payloadEnabled) {
        this.payloadEnabled = payloadEnabled;
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

    public boolean isAsyncEnabled() {
        return asyncEnabled;
    }

    public void setAsyncEnabled(boolean asyncEnabled) {
        this.asyncEnabled = asyncEnabled;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }

    public String getUserHashSalt() {
        return userHashSalt;
    }

    public void setUserHashSalt(String userHashSalt) {
        this.userHashSalt = userHashSalt;
    }

    public Cost getCost() {
        return cost;
    }

    public static class Cost {
        private boolean enabled = false;
        private String currency = "CNY";
        private String configVersion = "v1";
        private Map<String, ModelCost> models = new HashMap<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getCurrency() {
            return currency;
        }

        public void setCurrency(String currency) {
            this.currency = currency;
        }

        public String getConfigVersion() {
            return configVersion;
        }

        public void setConfigVersion(String configVersion) {
            this.configVersion = configVersion;
        }

        public Map<String, ModelCost> getModels() {
            return models;
        }

        public void setModels(Map<String, ModelCost> models) {
            this.models = models;
        }
    }

    public static class ModelCost {
        private BigDecimal promptPer1k;
        private BigDecimal completionPer1k;

        public BigDecimal getPromptPer1k() {
            return promptPer1k;
        }

        public void setPromptPer1k(BigDecimal promptPer1k) {
            this.promptPer1k = promptPer1k;
        }

        public BigDecimal getCompletionPer1k() {
            return completionPer1k;
        }

        public void setCompletionPer1k(BigDecimal completionPer1k) {
            this.completionPer1k = completionPer1k;
        }
    }
}
