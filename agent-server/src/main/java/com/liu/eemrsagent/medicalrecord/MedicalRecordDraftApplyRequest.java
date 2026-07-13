package com.liu.eemrsagent.medicalrecord;

import java.math.BigInteger;

public record MedicalRecordDraftApplyRequest(
        String department,
        String medication,
        String conditionDescription,
        String cost,
        BigInteger visitTime,
        String patientName,
        BigInteger age,
        String doctorName,
        String gender
) {
}
