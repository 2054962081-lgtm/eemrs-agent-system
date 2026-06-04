package com.liu.eemrsagent.llm;

import java.util.Locale;

public enum LlmProviderType {
    DEEPSEEK("deepseek"),
    OLLAMA("ollama");

    private final String value;

    LlmProviderType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static LlmProviderType from(String value) {
        if (value == null || value.isBlank()) {
            return DEEPSEEK;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (LlmProviderType type : values()) {
            if (type.value.equals(normalized)) {
                return type;
            }
        }
        throw new LlmException("Unsupported LLM provider: " + value);
    }
}
