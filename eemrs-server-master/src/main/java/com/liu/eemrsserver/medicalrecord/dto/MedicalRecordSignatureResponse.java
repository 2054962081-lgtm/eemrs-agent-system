package com.liu.eemrsserver.medicalrecord.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MedicalRecordSignatureResponse {
    @JsonProperty("dPk")
    @JsonAlias({"DPk", "dpk"})
    private String dPk;
    private String signature;
}
