package com.liu.eemrsserver.memory.dto;

import lombok.Data;

@Data
public class LongTermMemoryConfirmRequest {
    private String sessionId;
    private String department;
}
