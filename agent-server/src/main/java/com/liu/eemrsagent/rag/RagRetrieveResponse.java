package com.liu.eemrsagent.rag;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record RagRetrieveResponse(
        boolean success,
        String query,
        List<RagChunk> chunks,
        @JsonProperty("error_message") String errorMessage
) {
}
