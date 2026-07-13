package com.liu.eemrsagent.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EvalReportWriterTest {
    @TempDir
    Path tempDir;

    @Test
    void writesCsvJsonAndMarkdownWithoutFullUserText() throws Exception {
        TraceBasedEvaluator evaluator = new TraceBasedEvaluator(new ObjectMapper());
        EvalResult result = evaluator.evaluate(EvalTestFixtures.chestPainCase(), EvalTestFixtures.passingTrace());
        result.setStatus("PASSED");
        result.setFailureStage(FailureStage.NONE);

        EvalReportWriter.OutputFiles files = new EvalReportWriter(new ObjectMapper()).write(
                tempDir.resolve("nested").resolve("eval"),
                List.of(result),
                new EvalReportWriter.Metadata("preconsultation-eval-v1", "classpath:eval/preconsultation_eval_cases.json",
                        "mock-model", "preconsultation-deep-v1", "rag-v1", "1", "test")
        );

        assertThat(files.evalResultsCsv()).exists();
        assertThat(files.badcaseAnalysisJson()).exists();
        assertThat(files.evalReportMd()).exists();
        assertThat(Files.readString(files.evalResultsCsv())).contains("case_id,source,scenario");
        assertThat(Files.readString(files.badcaseAnalysisJson())).contains("failure_stage_distribution");
        assertThat(Files.readString(files.evalReportMd())).contains("dataset_version");
        assertThat(Files.readString(files.evalReportMd())).doesNotContain("我胸口疼了三天");
    }
}
