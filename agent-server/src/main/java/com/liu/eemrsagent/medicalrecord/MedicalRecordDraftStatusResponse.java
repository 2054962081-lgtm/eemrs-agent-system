package com.liu.eemrsagent.medicalrecord;

import java.time.LocalDateTime;

public record MedicalRecordDraftStatusResponse(
        Long draftId,
        String status,
        LocalDateTime firstReviewedAt,
        LocalDateTime completedAt,
        LocalDateTime appliedAt
) {
}
