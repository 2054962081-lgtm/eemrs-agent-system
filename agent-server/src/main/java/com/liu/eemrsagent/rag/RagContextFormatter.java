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
        return formatWithTrace(chunks, maxContextChars).context();
    }

    public FormattedContext formatWithTrace(List<RagChunk> chunks, int maxContextChars) {
        if (chunks == null || chunks.isEmpty() || maxContextChars <= 0) {
            return new FormattedContext("", List.of(), List.of(), List.of(), "empty_or_disabled");
        }
        Map<String, RagChunk> unique = new LinkedHashMap<>();
        List<String> duplicateChunkIds = new java.util.ArrayList<>();
        for (RagChunk chunk : chunks) {
            if (chunk == null || chunk.chunkId() == null || chunk.chunkId().isBlank()) {
                continue;
            }
            if (unique.containsKey(chunk.chunkId())) {
                duplicateChunkIds.add(chunk.chunkId());
            } else {
                unique.put(chunk.chunkId(), chunk);
            }
        }
        if (unique.isEmpty()) {
            return new FormattedContext("", List.of(), duplicateChunkIds, List.of(), "no_valid_chunk_id");
        }

        List<RagChunk> ordered = unique.values().stream()
                .sorted(Comparator
                        .comparingInt((RagChunk chunk) -> DOC_TYPE_PRIORITY.getOrDefault(chunk.docType(), 99))
                        .thenComparing((RagChunk chunk) -> chunk.score() == null ? 0.0 : -chunk.score()))
                .toList();

        StringBuilder builder = new StringBuilder();
        builder.append("【RAG 检索知识】仅用于辅助预问诊、分诊和病历摘要，不代表最终诊断。\n");
        int index = 1;
        List<String> finalChunkOrder = new java.util.ArrayList<>();
        List<String> droppedChunkIds = new java.util.ArrayList<>();
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
                droppedChunkIds.add(chunk.chunkId());
                break;
            }
            builder.append(item);
            finalChunkOrder.add(chunk.chunkId());
            index++;
        }
        if (index == 1) {
            droppedChunkIds.addAll(ordered.stream().map(RagChunk::chunkId).toList());
            return new FormattedContext("", List.of(), duplicateChunkIds, droppedChunkIds, "max_context_chars");
        }
        return new FormattedContext(builder.toString().trim(), finalChunkOrder, duplicateChunkIds, droppedChunkIds,
                droppedChunkIds.isEmpty() ? "none" : "max_context_chars");
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

    public record FormattedContext(
            String context,
            List<String> finalContextChunkOrder,
            List<String> duplicateChunkIds,
            List<String> droppedChunkIds,
            String dropReason
    ) {
    }
}
