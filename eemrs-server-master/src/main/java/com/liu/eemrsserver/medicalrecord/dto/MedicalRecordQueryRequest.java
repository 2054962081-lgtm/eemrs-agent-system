package com.liu.eemrsserver.medicalrecord.dto;

import lombok.Data;

import java.math.BigInteger;

@Data
public class MedicalRecordQueryRequest {
    private BigInteger startTime;
    private BigInteger endTime;
    private BigInteger minAge;
    private BigInteger maxAge;
    private String patientIdNumber;
    private String doctorIdNumber;
    private String doctorName;
    private String department;
}
