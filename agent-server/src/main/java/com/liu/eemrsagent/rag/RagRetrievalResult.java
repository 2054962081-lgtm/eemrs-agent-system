package com.liu.eemrsagent.rag;

import java.util.List;
import java.util.Map;

public record RagRetrievalResult(
        List<RagChunk> chunks,
        String expandedQuery,
        Map<String, Integer> docTypeCounts,
        boolean usedQueryExpansion,
        Map<String, Object> traceMeta
) {
    public static RagRetrievalResult empty() {
        return new RagRetrievalResult(List.of(), "", Map.of(), false, Map.of());
    }
}
