package com.liu.eemrsserver.memory.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class MemoryContextDTO {
    private Map<String, Object> longTermMemory;
    private List<Map<String, Object>> mediumTermMemory = new ArrayList<>();
    private ShortTermMemoryDTO shortTermMemory;
    private List<MemorySearchResultDTO> relatedUserMemory = new ArrayList<>();
}
