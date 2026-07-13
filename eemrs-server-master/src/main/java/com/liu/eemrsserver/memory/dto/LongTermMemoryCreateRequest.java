package com.liu.eemrsserver.memory.dto;

import lombok.Data;

@Data
public class LongTermMemoryCreateRequest {
    private String memoryType;
    private String memoryKey;
    private String memoryValue;
    private String severity;
    private String relation;
    private String evidence;
    private String source;
    private Integer confirmed;
    private String department;
}
