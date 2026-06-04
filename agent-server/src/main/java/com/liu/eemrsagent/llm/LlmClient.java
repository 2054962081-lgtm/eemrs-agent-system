package com.liu.eemrsagent.llm;

public interface LlmClient {
    LlmChatResponse chat(LlmChatRequest request);

    String providerName();
}
