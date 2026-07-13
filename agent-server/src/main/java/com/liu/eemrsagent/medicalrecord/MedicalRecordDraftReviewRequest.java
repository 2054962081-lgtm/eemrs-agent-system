package com.liu.eemrsagent.medicalrecord;

import com.fasterxml.jackson.databind.JsonNode;

public record MedicalRecordDraftReviewRequest(
        JsonNode editedRecordJson,
        String rejectReason,
        String comment
) {
}
