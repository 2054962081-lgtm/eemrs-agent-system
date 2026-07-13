package com.liu.eemrsagent.rag;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record RagChunk(
        @JsonProperty("chunk_id") String chunkId,
        @JsonProperty("doc_id") String docId,
        @JsonProperty("doc_type") String docType,
        String title,
        @JsonProperty("urgency_level") String urgencyLevel,
        @JsonProperty("related_departments") String relatedDepartments,
        @JsonProperty("applicable_population") String applicablePopulation,
        @JsonProperty("related_symptoms") String relatedSymptoms,
        @JsonProperty("must_ask") List<String> mustAsk,
        @JsonProperty("red_flags") List<String> redFlags,
        @JsonProperty("forbidden_actions") List<String> forbiddenActions,
        @JsonProperty("expected_response_points") List<String> expectedResponsePoints,
        @JsonProperty("doctor_record_fields") List<String> doctorRecordFields,
        Double score,
        @JsonProperty("chunk_text") String chunkText
) {
    public RagChunk(
            String chunkId,
            String docId,
            String docType,
            String title,
            String urgencyLevel,
            String relatedDepartments,
            Double score,
            String chunkText
    ) {
        this(
                chunkId,
                docId,
                docType,
                title,
                urgencyLevel,
                relatedDepartments,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                score,
                chunkText
        );
    }
}
