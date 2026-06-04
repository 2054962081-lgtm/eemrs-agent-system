package com.liu.eemrsagent.medicalrecord;

import java.util.List;

public record MedicalRecordDraftGenerateRequest(
        String sessionId,
        Long patientId,
        String patientIdNumber,
        String mode,
        String consultationConclusion,
        List<AgentMessage> history
) {
    public void validate() {
        if (!"deep".equalsIgnoreCase(mode == null ? "" : mode.trim())) {
            throw new IllegalArgumentException("Only deep consultation can generate medical record drafts");
        }
        if (consultationConclusion == null || consultationConclusion.trim().isEmpty()) {
            throw new IllegalArgumentException("consultationConclusion is required");
        }
        boolean hasUserInput = history != null && history.stream()
                .anyMatch(message -> message != null
                        && "user".equalsIgnoreCase(message.role())
                        && message.content() != null
                        && !message.content().trim().isEmpty());
        if (!hasUserInput) {
            throw new IllegalArgumentException("history must include at least one user message");
        }
    }

    public String normalizedSessionId() {
        return sessionId == null || sessionId.isBlank() ? null : sessionId.trim();
    }

    public String normalizedPatientIdNumber() {
        return patientIdNumber == null || patientIdNumber.isBlank() ? null : patientIdNumber.trim();
    }

    public String normalizedConclusion() {
        return consultationConclusion.trim();
    }
}
