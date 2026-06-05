package com.liu.eemrsagent.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RagContextFormatterTest {

    private final RagContextFormatter formatter = new RagContextFormatter();

    @Test
    void emptyChunksReturnEmptyString() {
        assertThat(formatter.format(List.of(), 3000)).isEmpty();
    }

    @Test
    void redFlagHasHigherPriorityAndDuplicateChunkIdIsRemoved() {
        RagChunk symptom = chunk("same", "symptom_inquiry", "症状模板", "症状内容");
        RagChunk redFlag = chunk("red", "red_flag", "红旗规则", "红旗内容");
        RagChunk duplicate = chunk("same", "department_triage", "重复", "重复内容");

        String context = formatter.format(List.of(symptom, redFlag, duplicate), 3000);

        assertThat(context.indexOf("红旗规则")).isLessThan(context.indexOf("症状模板"));
        assertThat(context).contains("症状模板");
        assertThat(context).doesNotContain("重复内容");
    }

    @Test
    void longChunkTextIsTruncated() {
        String context = formatter.format(List.of(chunk("c1", "red_flag", "长文本", "甲".repeat(900))), 3000);

        assertThat(context).contains("...");
        assertThat(context).hasSizeLessThan(1200);
    }

    @Test
    void maxContextCharsIsRespected() {
        String context = formatter.format(List.of(
                chunk("c1", "red_flag", "标题1", "甲".repeat(500)),
                chunk("c2", "symptom_inquiry", "标题2", "乙".repeat(500))
        ), 900);

        assertThat(context.length()).isLessThanOrEqualTo(900);
        assertThat(context).contains("RAG 检索知识");
        assertThat(context).contains("标题1");
        assertThat(context).doesNotContain("标题2");
    }

    private RagChunk chunk(String id, String type, String title, String text) {
        return new RagChunk(id, "doc-" + id, type, title, "急诊", "急诊科", 0.9, text);
    }
}
