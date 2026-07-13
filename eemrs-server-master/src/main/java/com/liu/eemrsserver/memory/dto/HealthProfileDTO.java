package com.liu.eemrsserver.memory.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
public class HealthProfileDTO {
    private Long id;
    private String patientIdHash;
    private String idNumberHash;
    private String gender;
    private Date birthDate;
    private BigDecimal heightCm;
    private BigDecimal weightKg;
    private String bloodType;
    private String specialStatus;
    private String source;
    private Integer confirmed;
    private Integer active;
    private Date createdAt;
    private Date updatedAt;
}
