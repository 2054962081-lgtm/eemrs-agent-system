package com.liu.eemrsagent.medicalrecord;

public record MedicalRecordDraftActionResponse(
        boolean success,
        String message,
        MedicalRecordDraftDetail draft,
        boolean idempotent
) {
    public static MedicalRecordDraftActionResponse ok(String message, MedicalRecordDraftDetail draft) {
        return new MedicalRecordDraftActionResponse(true, message, draft, false);
    }

    public static MedicalRecordDraftActionResponse idempotent(String message, MedicalRecordDraftDetail draft) {
        return new MedicalRecordDraftActionResponse(true, message, draft, true);
    }
}
