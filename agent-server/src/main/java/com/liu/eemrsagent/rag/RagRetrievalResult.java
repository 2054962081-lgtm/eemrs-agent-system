package com.liu.eemrsagent.rag;

import java.util.List;
import java.util.Map;

public record RagRetrievalResult(
        List<RagChunk> chunks,
        String expandedQuery,
        Map<String, Integer> docTypeCounts,
        boolean usedQueryExpansion
) {
    public static RagRetrievalResult empty() {
        return new RagRetrievalResult(List.of(), "", Map.of(), false);
    }
}
