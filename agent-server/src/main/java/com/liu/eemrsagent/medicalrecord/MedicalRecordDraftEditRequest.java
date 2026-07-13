package com.liu.eemrsagent.medicalrecord;

import com.fasterxml.jackson.databind.JsonNode;

public record MedicalRecordDraftEditRequest(
        JsonNode editedRecordJson,
        String comment
) {
}
