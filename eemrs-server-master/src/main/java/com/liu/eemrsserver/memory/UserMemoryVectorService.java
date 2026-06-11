package com.liu.eemrsserver.memory;

import com.liu.eemrsserver.memory.dto.MemorySearchResultDTO;
import com.liu.eemrsserver.memory.dto.MemoryUserScopeDTO;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserMemoryVectorService {
    private static final Logger logger = Logger.getLogger(UserMemoryVectorService.class);

    @Autowired
    private MemoryProperties memoryProperties;

    private final RestTemplate restTemplate = new RestTemplate();

    public void upsertMemoryVector(MemoryUserScopeDTO scope,
                                   String text,
                                   String memoryLevel,
                                   String sourceType,
                                   String sourceId,
                                   String department,
                                   Long eventTime) {
        if (!isEnabled() || scope == null || scope.getPatientIdHash() == null || text == null || text.trim().isEmpty()) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("collection", memoryProperties.getVector().getCollection());
        body.put("text", text);
        body.put("metadata", buildMetadata(scope, memoryLevel, sourceType, sourceId, department, eventTime));
        try {
            restTemplate.postForObject(memoryProperties.getVector().getServiceUrl() + "/memory/upsert", body, Map.class);
            logger.info("User memory vector upsert requested, sourceType=" + blankToDefault(sourceType, "visit_summary")
                    + ", sourceIdPresent=" + (sourceId != null && !sourceId.trim().isEmpty()));
        } catch (RuntimeException ignored) {
            logger.warn("User memory vector upsert skipped because vector service is unavailable");
            // Vector memory is best-effort so the core EEMRS workflows do not depend on Milvus availability.
        }
    }

    public List<MemorySearchResultDTO> searchUserMemory(MemoryUserScopeDTO scope, String query, int topK) {
        List<MemorySearchResultDTO> result = new ArrayList<>();
        if (!isEnabled() || scope == null || scope.getPatientIdHash() == null || query == null || query.trim().isEmpty()) {
            return result;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("collection", memoryProperties.getVector().getCollection());
        body.put("query", query);
        body.put("topK", topK <= 0 ? 5 : topK);
        body.put("filter", buildFilterByPatientScope(scope));
        try {
            Map response = restTemplate.postForObject(memoryProperties.getVector().getServiceUrl() + "/memory/search", body, Map.class);
            List<MemorySearchResultDTO> parsed = parseSearchResults(response);
            logger.info("User memory vector search completed, resultCount=" + parsed.size());
            return parsed;
        } catch (RuntimeException ignored) {
            logger.warn("User memory vector search skipped because vector service is unavailable");
            return result;
        }
    }

    public void deleteMemoryBySourceId(MemoryUserScopeDTO scope, String sourceId) {
        deleteMemoryBySourceId(scope, null, sourceId);
    }

    public void deleteMemoryBySourceId(MemoryUserScopeDTO scope, String sourceType, String sourceId) {
        if (!isEnabled() || scope == null || scope.getPatientIdHash() == null || sourceId == null || sourceId.trim().isEmpty()) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("collection", memoryProperties.getVector().getCollection());
        body.put("sourceId", sourceId.trim());
        if (sourceType != null && !sourceType.trim().isEmpty()) {
            body.put("sourceType", sourceType.trim());
        }
        body.put("filter", buildFilterByPatientScope(scope));
        try {
            restTemplate.postForObject(memoryProperties.getVector().getServiceUrl() + "/memory/delete-by-source", body, Map.class);
        } catch (RuntimeException ignored) {
            logger.warn("User memory vector delete skipped because vector service is unavailable");
            return;
        }
    }

    public String buildFilterByPatientScope(MemoryUserScopeDTO scope) {
        if (scope == null || scope.getPatientIdHash() == null || scope.getPatientIdHash().trim().isEmpty()) {
            return "patientIdHash == '__empty__'";
        }
        return "patientIdHash == '" + scope.getPatientIdHash().replace("'", "\\'") + "'";
    }

    private Map<String, Object> buildMetadata(MemoryUserScopeDTO scope,
                                              String memoryLevel,
                                              String sourceType,
                                              String sourceId,
                                              String department,
                                              Long eventTime) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("patientIdHash", scope.getPatientIdHash());
        metadata.put("memoryLevel", blankToDefault(memoryLevel, "medium"));
        metadata.put("sourceType", blankToDefault(sourceType, "visit_summary"));
        metadata.put("sourceId", blankToDefault(sourceId, ""));
        metadata.put("department", blankToDefault(department, ""));
        metadata.put("eventTime", eventTime);
        metadata.put("createdAt", System.currentTimeMillis());
        return metadata;
    }

    private boolean isEnabled() {
        return memoryProperties.getVector().isEnabled()
                && memoryProperties.getVector().getServiceUrl() != null
                && !memoryProperties.getVector().getServiceUrl().trim().isEmpty();
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.trim().isEmpty() ? defaultValue : value.trim();
    }

    private List<MemorySearchResultDTO> parseSearchResults(Map response) {
        if (response == null || !Boolean.TRUE.equals(response.get("success"))) {
            return Collections.emptyList();
        }
        Object rawResults = response.get("results");
        if (!(rawResults instanceof List)) {
            return Collections.emptyList();
        }
        List<MemorySearchResultDTO> parsed = new ArrayList<>();
        for (Object rawItem : (List) rawResults) {
            if (!(rawItem instanceof Map)) {
                continue;
            }
            Map item = (Map) rawItem;
            MemorySearchResultDTO dto = new MemorySearchResultDTO();
            dto.setId(asString(item.get("id")));
            dto.setText(asString(item.get("text")));
            dto.setScore(asDouble(item.get("score")));
            Object metadata = item.get("metadata");
            if (metadata instanceof Map) {
                dto.setMetadata((Map<String, Object>) metadata);
            }
            parsed.add(dto);
        }
        return parsed;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Double asDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Double.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
