package com.liu.eemrsagent.llm;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

@Component
public class LlmClientFactory {

    public static final String PURPOSE_PRE_CONSULTATION = "pre_consultation";
    public static final String PURPOSE_MEDICAL_RECORD_DRAFT = "medical_record_draft";
    public static final String PURPOSE_PRIVACY = "privacy";

    private final LlmProperties properties;
    private final Map<LlmProviderType, LlmClient> clients = new EnumMap<>(LlmProviderType.class);

    public LlmClientFactory(LlmProperties properties, DeepSeekClient deepSeekClient, OllamaClient ollamaClient) {
        this.properties = properties;
        clients.put(LlmProviderType.DEEPSEEK, deepSeekClient);
        clients.put(LlmProviderType.OLLAMA, ollamaClient);
    }

    public LlmChatResponse chatForPurpose(String purpose, LlmChatRequest request) {
        LlmProviderType primaryProvider = providerForPurpose(purpose);
        try {
            return client(primaryProvider).chat(request);
        } catch (LlmException e) {
            if (!properties.getFallback().isEnabled()) {
                throw e;
            }
            LlmProviderType fallbackProvider = LlmProviderType.from(properties.getFallback().getFallbackProvider());
            if (fallbackProvider == primaryProvider) {
                throw e;
            }
            return client(fallbackProvider).chat(request);
        }
    }

    public LlmProviderType providerForPurpose(String purpose) {
        if (PURPOSE_PRE_CONSULTATION.equals(purpose)) {
            return LlmProviderType.from(properties.getRouting().getPreConsultationProvider());
        }
        if (PURPOSE_MEDICAL_RECORD_DRAFT.equals(purpose)) {
            return LlmProviderType.from(properties.getRouting().getMedicalRecordDraftProvider());
        }
        if (PURPOSE_PRIVACY.equals(purpose)) {
            return LlmProviderType.from(properties.getRouting().getPrivacyProvider());
        }
        return LlmProviderType.from(properties.getDefaultProvider());
    }

    private LlmClient client(LlmProviderType providerType) {
        LlmClient client = clients.get(providerType);
        if (client == null) {
            throw new LlmException("LLM provider is not available: " + providerType.value());
        }
        return client;
    }
}
