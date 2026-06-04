package com.liu.eemrsagent.llm;

public record LlmChatResponse(
        String content,
        String model,
        String provider,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        String rawResponse
) {
}
