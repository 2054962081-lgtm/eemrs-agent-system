package com.liu.eemrsagent.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "llm")
public class LlmProperties {

    private String defaultProvider = "deepseek";
    private final DeepSeek deepseek = new DeepSeek();
    private final Ollama ollama = new Ollama();
    private final Routing routing = new Routing();
    private final Fallback fallback = new Fallback();

    public String getDefaultProvider() {
        return defaultProvider;
    }

    public void setDefaultProvider(String defaultProvider) {
        this.defaultProvider = defaultProvider;
    }

    public DeepSeek getDeepseek() {
        return deepseek;
    }

    public Ollama getOllama() {
        return ollama;
    }

    public Routing getRouting() {
        return routing;
    }

    public Fallback getFallback() {
        return fallback;
    }

    public static class DeepSeek {
        private boolean enabled = true;
        private String baseUrl = "https://api.deepseek.com";
        private String apiKey = "";
        private String model = "deepseek-v4-flash";
        private int timeoutSeconds = 90;
        private int maxTokens = 4096;
        private double temperature = 0.2;
        private double topP = 0.8;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public int getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }

        public double getTopP() {
            return topP;
        }

        public void setTopP(double topP) {
            this.topP = topP;
        }
    }

    public static class Ollama {
        private boolean enabled = true;
        private String baseUrl = "http://localhost:11434";
        private String model = "qwen3-vl:8b";
        private int timeoutSeconds = 120;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }

    public static class Routing {
        private String preConsultationProvider = "deepseek";
        private String medicalRecordDraftProvider = "deepseek";
        private String privacyProvider = "ollama";

        public String getPreConsultationProvider() {
            return preConsultationProvider;
        }

        public void setPreConsultationProvider(String preConsultationProvider) {
            this.preConsultationProvider = preConsultationProvider;
        }

        public String getMedicalRecordDraftProvider() {
            return medicalRecordDraftProvider;
        }

        public void setMedicalRecordDraftProvider(String medicalRecordDraftProvider) {
            this.medicalRecordDraftProvider = medicalRecordDraftProvider;
        }

        public String getPrivacyProvider() {
            return privacyProvider;
        }

        public void setPrivacyProvider(String privacyProvider) {
            this.privacyProvider = privacyProvider;
        }
    }

    public static class Fallback {
        private boolean enabled = false;
        private String fallbackProvider = "ollama";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getFallbackProvider() {
            return fallbackProvider;
        }

        public void setFallbackProvider(String fallbackProvider) {
            this.fallbackProvider = fallbackProvider;
        }
    }
}
