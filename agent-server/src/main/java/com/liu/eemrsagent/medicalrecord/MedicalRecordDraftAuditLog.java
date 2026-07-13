package com.liu.eemrsagent.medicalrecord;

import java.time.LocalDateTime;

public record MedicalRecordDraftAuditLog(
        Long id,
        Long draftId,
        String doctorIdNumber,
        MedicalRecordDraftAction action,
        String beforeJson,
        String afterJson,
        String rejectReason,
        String comment,
        LocalDateTime actionTime,
        String traceId
) {
}
