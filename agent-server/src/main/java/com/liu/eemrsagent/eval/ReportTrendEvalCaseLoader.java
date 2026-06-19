package com.liu.eemrsagent.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ReportTrendEvalCaseLoader {
    private final ObjectMapper objectMapper;

    public ReportTrendEvalCaseLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    }

    public List<ReportTrendEvalCase> load(String file) throws IOException {
        Path path = Path.of(file);
        if (!Files.exists(path) || Files.size(path) == 0) {
            throw new IllegalArgumentException("Report trend eval case file is empty or missing: " + file);
        }
        List<ReportTrendEvalCase> cases = objectMapper.readValue(path.toFile(), new TypeReference<>() {});
        if (cases == null || cases.isEmpty()) {
            throw new IllegalArgumentException("Report trend eval case file has no cases: " + file);
        }
        Set<String> ids = new HashSet<>();
        for (ReportTrendEvalCase item : cases) {
            if (item.getCaseId() == null || item.getCaseId().isBlank()) {
                throw new IllegalArgumentException("case_id is required");
            }
            if (!ids.add(item.getCaseId())) {
                throw new IllegalArgumentException("Duplicate case_id: " + item.getCaseId());
            }
        }
        return cases;
    }
}
