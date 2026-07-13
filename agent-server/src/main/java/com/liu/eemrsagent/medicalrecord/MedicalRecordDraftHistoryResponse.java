package com.liu.eemrsagent.medicalrecord;

import java.util.List;

public record MedicalRecordDraftHistoryResponse(
        Long draftId,
        List<MedicalRecordDraftAuditLog> logs
) {
}
