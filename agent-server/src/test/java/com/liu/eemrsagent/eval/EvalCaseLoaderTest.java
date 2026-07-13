package com.liu.eemrsagent.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvalCaseLoaderTest {
    private final EvalCaseLoader loader = new EvalCaseLoader(new ObjectMapper());

    @Test
    void loadsStandardJsonAndValidatesRequiredFields() throws Exception {
        List<EvalCase> cases = loader.load("classpath:eval/preconsultation_eval_cases.json");

        assertThat(cases).hasSize(25);
        assertThat(cases).extracting(EvalCase::getCaseId).doesNotHaveDuplicates();
        assertThat(cases).allSatisfy(evalCase -> {
            assertThat(evalCase.getSource()).isNotBlank();
            assertThat(evalCase.getExpected()).isNotNull();
            assertThat(evalCase.getTurns()).isNotEmpty();
        });
        assertThat(cases).anyMatch(evalCase -> "existing_eval_set".equals(evalCase.getSource()));
        assertThat(cases).anyMatch(evalCase -> "added_harness_v2".equals(evalCase.getSource()));
    }

    @Test
    void rejectsDuplicateCaseIds() {
        assertThatThrownBy(() -> loader.load("classpath:eval/invalid/duplicate_case_ids.json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate case_id");
    }

    @Test
    void rejectsEmptyCaseFile() {
        assertThatThrownBy(() -> loader.load("classpath:eval/invalid/empty_cases.json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-empty cases");
    }

    @Test
    void rejectsMissingRequiredFields() {
        String json = "{\"cases\":[{\"case_id\":\"X\"}]}";

        assertThatThrownBy(() -> loader.load(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("source");
    }
}
