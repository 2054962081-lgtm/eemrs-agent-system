package com.liu.eemrsserver.memory.dto;

import lombok.Data;

import java.util.Date;

@Data
public class LongTermMemoryDTO {
    private Long id;
    private String patientIdHash;
    private String memoryType;
    private String memoryKey;
    private String memoryValue;
    private String severity;
    private String relation;
    private String evidence;
    private String source;
    private Integer confirmed;
    private Integer active;
    private Date createdAt;
    private Date updatedAt;
}
