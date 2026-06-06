package com.liu.eemrsagent.rag;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public record RagRetrieveResponse(
        boolean success,
        String query,
        @JsonProperty("expanded_query") String expandedQuery,
        @JsonProperty("doc_type_counts") Map<String, Integer> docTypeCounts,
        @JsonProperty("used_query_expansion") Boolean usedQueryExpansion,
        List<RagChunk> chunks,
        @JsonProperty("error_message") String errorMessage
) {
}
