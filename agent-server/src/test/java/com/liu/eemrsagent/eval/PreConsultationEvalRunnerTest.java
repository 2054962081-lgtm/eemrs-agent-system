package com.liu.eemrsagent.eval;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PreConsultationEvalRunnerTest {
    @TempDir
    Path tempDir;

    @Test
    void mockRunnerContinuesAndWritesReports() throws Exception {
        PreConsultationTraceEvalRunner runner = PreConsultationTraceEvalRunner.mockRunner();

        EvalReportWriter.OutputFiles files = runner.run(new PreConsultationTraceEvalRunner.Config(
                "classpath:eval/preconsultation_eval_cases.json",
                tempDir,
                3,
                false,
                new EvalReportWriter.Metadata("preconsultation-eval-v1", "classpath:eval/preconsultation_eval_cases.json",
                        "mock-model", "preconsultation-deep-v1", "mock-rag", "1", "test")
        ));

        assertThat(files.evalResultsCsv()).exists();
        assertThat(files.badcaseAnalysisJson()).exists();
        assertThat(files.evalReportMd()).exists();
        assertThat(Files.readString(files.evalResultsCsv()).lines()).hasSize(4);
        assertThat(Files.readString(files.evalReportMd())).doesNotContain("咳嗽三天，嗓子痛");
    }
}
