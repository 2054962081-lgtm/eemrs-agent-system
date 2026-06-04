package com.liu.eemrsagent.medicalrecord;

public record MedicalRecordDraftQueryResponse(
        boolean success,
        boolean hasDraft,
        MedicalRecordDraftDetail draft,
        String message,
        String error
) {
    public static MedicalRecordDraftQueryResponse found(MedicalRecordDraftDetail draft) {
        return new MedicalRecordDraftQueryResponse(true, true, draft, "ok", null);
    }

    public static MedicalRecordDraftQueryResponse empty() {
        return new MedicalRecordDraftQueryResponse(true, false, null, "暂无预问诊病历草稿", null);
    }

    public static MedicalRecordDraftQueryResponse fail(String message, String error) {
        return new MedicalRecordDraftQueryResponse(false, false, null, message, error);
    }
}
