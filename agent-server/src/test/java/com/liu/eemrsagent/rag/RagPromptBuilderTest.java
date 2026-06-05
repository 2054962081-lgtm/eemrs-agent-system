package com.liu.eemrsagent.rag;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RagPromptBuilderTest {

    private final RagPromptBuilder builder = new RagPromptBuilder();

    @Test
    void emptyRagContextKeepsOriginalPrompt() {
        assertThat(builder.mergeSystemPrompt("base prompt", "")).isEqualTo("base prompt");
    }

    @Test
    void nonEmptyRagContextAddsSafetyRules() {
        String prompt = builder.mergeSystemPrompt("base prompt", "RAG context");

        assertThat(prompt).contains("base prompt");
        assertThat(prompt).contains("RAG context");
        assertThat(prompt).contains("不代表最终诊断");
        assertThat(prompt).contains("不要开具处方");
        assertThat(prompt).contains("即使建议急诊，也要补充 3-5 个最关键追问");
    }
}
