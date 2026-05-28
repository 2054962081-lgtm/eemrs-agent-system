package com.liu.eemrsserver.medicalrecord.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigInteger;

@Data
public class MedicalRecordRequest {
    private String department;
    private String medication;
    private String conditionDescription;
    private String cost;
    private BigInteger visitTime;
    private String patientName;
    private String patientIdNumber;
    private BigInteger age;
    private String doctorName;
    private String doctorIdNumber;
    @JsonProperty("dPk")
    @JsonAlias({"DPk", "dpk"})
    private String dPk;
    private String signature;
    private String gender;
}
