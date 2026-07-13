package com.liu.eemrsagent.medicalrecord;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

public record MedicalRecordDraftDetail(
        Long id,
        Long patientId,
        String patientIdNumber,
        String doctorIdNumber,
        String appointmentId,
        String sessionId,
        String consultationMode,
        String sourceType,
        String chiefComplaint,
        String presentIllnessHistory,
        String recommendedDepartment,
        String urgency,
        String consultationSummary,
        JsonNode aiRecordJson,
        JsonNode editedRecordJson,
        JsonNode recordJson,
        String status,
        String modelName,
        String promptVersion,
        String traceId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime firstReviewedAt,
        LocalDateTime completedAt,
        LocalDateTime appliedAt,
        boolean parseError
) {
}
