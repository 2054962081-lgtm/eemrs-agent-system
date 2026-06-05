package com.liu.eemrsagent.rag;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RagChunk(
        @JsonProperty("chunk_id") String chunkId,
        @JsonProperty("doc_id") String docId,
        @JsonProperty("doc_type") String docType,
        String title,
        @JsonProperty("urgency_level") String urgencyLevel,
        @JsonProperty("related_departments") String relatedDepartments,
        Double score,
        @JsonProperty("chunk_text") String chunkText
) {
}
