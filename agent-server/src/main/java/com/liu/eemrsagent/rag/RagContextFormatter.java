package com.liu.eemrsagent.rag;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class RagContextFormatter {

    private static final int CHUNK_TEXT_LIMIT = 520;

    private static final Map<String, Integer> DOC_TYPE_PRIORITY = Map.of(
            "red_flag", 1,
            "symptom_inquiry", 2,
            "special_population", 3,
            "department_triage", 4,
            "medical_record_template", 5
    );

    public String format(List<RagChunk> chunks, int maxContextChars) {
        if (chunks == null || chunks.isEmpty() || maxContextChars <= 0) {
            return "";
        }
        Map<String, RagChunk> unique = new LinkedHashMap<>();
        for (RagChunk chunk : chunks) {
            if (chunk == null || chunk.chunkId() == null || chunk.chunkId().isBlank()) {
                continue;
            }
            unique.putIfAbsent(chunk.chunkId(), chunk);
        }
        if (unique.isEmpty()) {
            return "";
        }

        List<RagChunk> ordered = unique.values().stream()
                .sorted(Comparator
                        .comparingInt((RagChunk chunk) -> DOC_TYPE_PRIORITY.getOrDefault(chunk.docType(), 99))
                        .thenComparing((RagChunk chunk) -> chunk.score() == null ? 0.0 : -chunk.score()))
                .toList();

        StringBuilder builder = new StringBuilder();
        builder.append("【RAG 检索知识】仅用于辅助预问诊、分诊和病历摘要，不代表最终诊断。\n");
        int index = 1;
        for (RagChunk chunk : ordered) {
            String item = """
                    %d. 标题：%s
                    类型：%s
                    紧急程度：%s
                    相关科室：%s
                    内容：%s

                    """.formatted(
                    index,
                    safe(chunk.title()),
                    safe(chunk.docType()),
                    safe(chunk.urgencyLevel()),
                    safe(chunk.relatedDepartments()),
                    truncate(safe(chunk.chunkText()), CHUNK_TEXT_LIMIT)
            );
            if (builder.length() + item.length() > maxContextChars) {
                break;
            }
            builder.append(item);
            index++;
        }
        return index == 1 ? "" : builder.toString().trim();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }
}
