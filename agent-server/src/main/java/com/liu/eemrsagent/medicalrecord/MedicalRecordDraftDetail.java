package com.liu.eemrsagent.medicalrecord;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

public record MedicalRecordDraftDetail(
        Long id,
        Long patientId,
        String patientIdNumber,
        String sessionId,
        String consultationMode,
        String sourceType,
        String chiefComplaint,
        String presentIllnessHistory,
        String recommendedDepartment,
        String urgency,
        String consultationSummary,
        JsonNode recordJson,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean parseError
) {
}
