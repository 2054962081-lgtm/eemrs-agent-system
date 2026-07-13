package com.liu.eemrsagent.medicalrecord;

public record MedicalRecordDraftEntity(
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
        String recordJson,
        String aiRecordJson,
        String editedRecordJson,
        String rawModelReply,
        String status,
        String modelName,
        String promptVersion,
        String traceId,
        String createdBy
) {
}
