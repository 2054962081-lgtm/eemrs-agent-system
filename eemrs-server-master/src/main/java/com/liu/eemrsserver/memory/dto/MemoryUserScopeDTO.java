package com.liu.eemrsserver.memory.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MemoryUserScopeDTO {
    private Long userId;
    private String idNumber;
    private String patientKey;
    private String patientIdHash;
    private String displayName;
}
