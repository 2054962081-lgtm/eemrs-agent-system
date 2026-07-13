package com.liu.eemrsserver.memory.dto;

import lombok.Data;

import java.util.Map;

@Data
public class MemorySearchResultDTO {
    private String id;
    private String text;
    private Double score;
    private Map<String, Object> metadata;
}
