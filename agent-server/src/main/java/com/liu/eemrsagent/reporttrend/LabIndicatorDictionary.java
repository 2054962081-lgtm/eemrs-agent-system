package com.liu.eemrsagent.reporttrend;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
public class LabIndicatorDictionary {
    private final Map<String, IndicatorDefinition> byCode = new LinkedHashMap<>();
    private final Map<String, IndicatorDefinition> byAlias = new LinkedHashMap<>();

    public LabIndicatorDictionary(ObjectMapper objectMapper) {
        try (InputStream input = new ClassPathResource("lab/lab_indicator_dictionary.json").getInputStream()) {
            List<IndicatorDefinition> definitions = objectMapper.readValue(input, new TypeReference<>() {
            });
            for (IndicatorDefinition definition : definitions) {
                byCode.put(normalize(definition.code()), definition);
                byAlias.put(normalize(definition.code()), definition);
                byAlias.put(normalize(definition.name()), definition);
                for (String alias : definition.aliases()) {
                    byAlias.put(normalize(alias), definition);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load lab indicator dictionary", e);
        }
    }

    public Optional<IndicatorDefinition> find(String rawNameOrCode) {
        if (rawNameOrCode == null || rawNameOrCode.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byAlias.get(normalize(rawNameOrCode)));
    }

    public List<IndicatorDefinition> all() {
        return new ArrayList<>(byCode.values());
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").replace("-", "_").toUpperCase(Locale.ROOT);
    }

    public record IndicatorDefinition(String code, String name, List<String> aliases) {
        public IndicatorDefinition {
            aliases = aliases == null ? List.of() : List.copyOf(aliases);
        }
    }
}
