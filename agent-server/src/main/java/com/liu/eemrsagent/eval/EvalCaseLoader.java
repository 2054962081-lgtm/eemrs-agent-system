package com.liu.eemrsagent.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class EvalCaseLoader {
    private final ObjectMapper objectMapper;

    public EvalCaseLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<EvalCase> load(String location) throws IOException {
        Resource resource = new PathMatchingResourcePatternResolver().getResource(location);
        try (InputStream inputStream = resource.getInputStream()) {
            return load(inputStream);
        }
    }

    public List<EvalCase> load(Path path) throws IOException {
        return load(path.toUri().toString());
    }

    public List<EvalCase> load(InputStream inputStream) throws IOException {
        JsonNode root = objectMapper.readTree(inputStream);
        JsonNode casesNode = root.isArray() ? root : root.get("cases");
        if (casesNode == null || !casesNode.isArray() || casesNode.isEmpty()) {
            throw new IllegalArgumentException("Eval case file must contain a non-empty cases array.");
        }
        List<EvalCase> cases = new ArrayList<>();
        Set<String> caseIds = new HashSet<>();
        for (JsonNode node : casesNode) {
            EvalCase evalCase = objectMapper.treeToValue(node, EvalCase.class);
            validate(evalCase);
            if (!caseIds.add(evalCase.getCaseId())) {
                throw new IllegalArgumentException("Duplicate case_id: " + evalCase.getCaseId());
            }
            cases.add(evalCase);
        }
        return cases;
    }

    private void validate(EvalCase evalCase) {
        require(evalCase.getCaseId(), "case_id");
        require(evalCase.getSource(), "source");
        require(evalCase.getScenario(), "scenario");
        require(evalCase.getCategory(), "category");
        if (evalCase.getTurns().isEmpty()) {
            throw new IllegalArgumentException(evalCase.getCaseId() + " must contain turns.");
        }
        if (evalCase.getExpected() == null) {
            throw new IllegalArgumentException(evalCase.getCaseId() + " must contain expected.");
        }
    }

    private void require(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required field: " + fieldName);
        }
    }
}
