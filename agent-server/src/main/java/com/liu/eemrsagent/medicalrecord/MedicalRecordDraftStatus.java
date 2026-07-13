package com.liu.eemrsagent.medicalrecord;

import java.util.Set;

public enum MedicalRecordDraftStatus {
    GENERATED,
    REVIEWING,
    ACCEPTED,
    PARTIALLY_ACCEPTED,
    REJECTED,
    APPLIED;

    public boolean canTransitionTo(MedicalRecordDraftStatus next) {
        if (this == next) {
            return true;
        }
        return switch (this) {
            case GENERATED -> Set.of(REVIEWING, ACCEPTED, PARTIALLY_ACCEPTED, REJECTED).contains(next);
            case REVIEWING -> Set.of(ACCEPTED, PARTIALLY_ACCEPTED, REJECTED).contains(next);
            case ACCEPTED, PARTIALLY_ACCEPTED -> next == APPLIED;
            case REJECTED, APPLIED -> false;
        };
    }

    public boolean canEdit() {
        return this == GENERATED || this == REVIEWING;
    }

    public boolean canApply() {
        return this == ACCEPTED || this == PARTIALLY_ACCEPTED || this == APPLIED;
    }

    public static MedicalRecordDraftStatus parse(String value) {
        if (value == null || value.isBlank() || "DRAFT".equalsIgnoreCase(value)) {
            return GENERATED;
        }
        return MedicalRecordDraftStatus.valueOf(value.trim().toUpperCase());
    }
}
