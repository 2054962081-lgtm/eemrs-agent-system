package com.liu.eemrsagent.rag;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record RagRetrieveRequest(
        String query,
        @JsonProperty("top_k") Integer topK,
        @JsonProperty("include_doc_types") List<String> includeDocTypes,
        String scene
) {
}
