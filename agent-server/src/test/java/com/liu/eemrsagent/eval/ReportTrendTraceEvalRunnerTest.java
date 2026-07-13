package com.liu.eemrsagent.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ReportTrendTraceEvalRunnerTest {
    @Test
    void runsAtLeastThreeCasesAndWritesChineseReports(@TempDir Path tempDir) throws Exception {
        ReportTrendTraceEvalRunner runner = new ReportTrendTraceEvalRunner(new ObjectMapper());

        ReportTrendEvalReportWriter.OutputFiles files = runner.run(new ReportTrendTraceEvalRunner.Config(
                "src/test/resources/eval/report_trend_eval_cases.json",
                tempDir,
                3,
                false,
                new ReportTrendEvalReportWriter.Metadata("report-trend-v1", "report_trend_eval_cases.json")
        ));

        assertThat(Files.readString(files.evalResultsCsv()).split("\\R")).hasSizeGreaterThanOrEqualTo(4);
        assertThat(Files.exists(files.badcaseAnalysisJson())).isTrue();
        assertThat(Files.readString(files.evalReportMd())).contains("报告纵向分析 Harness V2 评测报告");
        assertThat(Files.readString(files.evalReportMd())).doesNotContain("API Key", "真实患者");
    }
}
