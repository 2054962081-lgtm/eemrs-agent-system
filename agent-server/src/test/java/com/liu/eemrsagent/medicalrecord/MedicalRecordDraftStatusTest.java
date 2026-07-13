package com.liu.eemrsagent.medicalrecord;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MedicalRecordDraftStatusTest {
    @Test
    void allowsExpectedTransitions() {
        assertTrue(MedicalRecordDraftStatus.GENERATED.canTransitionTo(MedicalRecordDraftStatus.ACCEPTED));
        assertTrue(MedicalRecordDraftStatus.GENERATED.canTransitionTo(MedicalRecordDraftStatus.PARTIALLY_ACCEPTED));
        assertTrue(MedicalRecordDraftStatus.GENERATED.canTransitionTo(MedicalRecordDraftStatus.REJECTED));
        assertTrue(MedicalRecordDraftStatus.PARTIALLY_ACCEPTED.canTransitionTo(MedicalRecordDraftStatus.APPLIED));
        assertTrue(MedicalRecordDraftStatus.ACCEPTED.canTransitionTo(MedicalRecordDraftStatus.APPLIED));
    }

    @Test
    void rejectsIllegalTransitions() {
        assertFalse(MedicalRecordDraftStatus.REJECTED.canTransitionTo(MedicalRecordDraftStatus.APPLIED));
        assertFalse(MedicalRecordDraftStatus.APPLIED.canTransitionTo(MedicalRecordDraftStatus.REVIEWING));
        assertFalse(MedicalRecordDraftStatus.GENERATED.canTransitionTo(MedicalRecordDraftStatus.APPLIED));
    }
}
