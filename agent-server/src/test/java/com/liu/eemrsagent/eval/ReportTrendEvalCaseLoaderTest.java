package com.liu.eemrsagent.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportTrendEvalCaseLoaderTest {
    private final ReportTrendEvalCaseLoader loader = new ReportTrendEvalCaseLoader(new ObjectMapper());

    @Test
    void loadsReportTrendCasesAndExpectedFields() throws Exception {
        List<ReportTrendEvalCase> cases = loader.load("src/test/resources/eval/report_trend_eval_cases.json");

        assertThat(cases).hasSizeGreaterThanOrEqualTo(12);
        assertThat(cases).extracting(ReportTrendEvalCase::getCaseId).doesNotHaveDuplicates();
        assertThat(cases.stream().flatMap(c -> c.getExpected().getExpectedAbnormalItems().stream())).isNotEmpty();
        assertThat(cases.stream().flatMap(c -> c.getExpected().getExpectedTrends().stream())).isNotEmpty();
        assertThat(cases.stream().flatMap(c -> c.getExpected().getExpectedContextLinks().stream())).isNotEmpty();
        assertThat(cases.stream().flatMap(c -> c.getExpected().getForbiddenBehaviors().stream())).isNotEmpty();
    }

    @Test
    void rejectsEmptyAndDuplicateCaseFiles(@TempDir Path tempDir) throws Exception {
        Path empty = tempDir.resolve("empty.json");
        Files.writeString(empty, "");
        Path duplicate = tempDir.resolve("duplicate.json");
        Files.writeString(duplicate, """
                [{"case_id":"x","expected":{}},{"case_id":"x","expected":{}}]
                """);

        assertThatThrownBy(() -> loader.load(empty.toString())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> loader.load(duplicate.toString())).isInstanceOf(IllegalArgumentException.class);
    }
}
