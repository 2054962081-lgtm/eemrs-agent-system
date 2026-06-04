package com.liu.eemrsagent.llm;

import java.util.List;

public record LlmChatRequest(
        List<LlmMessage> messages,
        String purpose,
        Double temperature,
        Double topP,
        Integer maxTokens,
        Boolean jsonMode,
        Boolean stream
) {
    public boolean streamEnabled() {
        return Boolean.TRUE.equals(stream);
    }

    public boolean jsonModeEnabled() {
        return Boolean.TRUE.equals(jsonMode);
    }
}
