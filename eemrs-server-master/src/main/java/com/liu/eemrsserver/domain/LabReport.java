package com.liu.eemrsserver.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LabReport {
    private Long id;
    private String patientIdHashCode;
    private String reportToken;
    private String reportPayloadCipher;
    private String departmentCipher;
    private BigInteger reportTimeOpe;
    private String reportTypeCipher;
    private String imageCipherUrl;
    private Date createdAt;
    private Date updatedAt;

    private String reportPayload;
    private String department;
    private BigInteger reportTime;
    private String reportType;
}
