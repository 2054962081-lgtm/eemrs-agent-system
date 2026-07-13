package com.liu.eemrsserver.memory.dto;

import lombok.Data;

@Data
public class LongTermMemoryCandidateDTO {
    private String candidateId;
    private String sessionId;
    private String memoryType;
    private String memoryKey;
    private String memoryValue;
    private String severity;
    private String relation;
    private String evidence;
    private Double confidence;
    private Boolean needConfirm;
}
