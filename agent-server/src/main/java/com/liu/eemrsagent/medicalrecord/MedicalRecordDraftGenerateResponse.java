package com.liu.eemrsagent.medicalrecord;

import com.fasterxml.jackson.databind.JsonNode;

public record MedicalRecordDraftGenerateResponse(
        boolean success,
        Long draftId,
        String message,
        JsonNode record,
        String error,
        String errorCode
) {
    public static MedicalRecordDraftGenerateResponse ok(Long draftId, JsonNode record) {
        return new MedicalRecordDraftGenerateResponse(true, draftId, "病历草稿生成成功", record, null, null);
    }

    public static MedicalRecordDraftGenerateResponse fail(String error) {
        return fail("DRAFT_GENERATION_FAILED", error);
    }

    public static MedicalRecordDraftGenerateResponse fail(String errorCode, String error) {
        return new MedicalRecordDraftGenerateResponse(false, null, "病历草稿生成失败，请稍后重试。", null, error, errorCode);
    }
}
