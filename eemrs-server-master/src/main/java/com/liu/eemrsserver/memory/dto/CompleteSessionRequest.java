package com.liu.eemrsserver.memory.dto;

import lombok.Data;

@Data
public class CompleteSessionRequest {
    private String summary;
    private String department;
    private String sourceId;
}
