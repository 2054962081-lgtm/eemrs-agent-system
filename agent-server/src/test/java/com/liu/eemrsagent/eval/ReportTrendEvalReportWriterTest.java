package com.liu.eemrsagent.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReportTrendEvalReportWriterTest {
    @Test
    void writesCsvJsonAndChineseMarkdownWithoutRawText(@TempDir Path tempDir) throws Exception {
        ReportTrendEvalResult result = new ReportTrendEvalResult();
        result.setCaseId("RT-1");
        result.setSource("test");
        result.setScenario("白细胞升高");
        result.setCategory("trend");
        result.setStatus("FAILED");
        result.setFailureStage(ReportTrendFailureStage.TREND_ANALYSIS_ERROR);
        result.setFailureReason("趋势方向不一致");

        ReportTrendEvalReportWriter.OutputFiles files = new ReportTrendEvalReportWriter(new ObjectMapper())
                .write(tempDir, List.of(result), new ReportTrendEvalReportWriter.Metadata("v1", "cases.json"));

        assertThat(Files.exists(files.evalResultsCsv())).isTrue();
        assertThat(Files.exists(files.badcaseAnalysisJson())).isTrue();
        assertThat(Files.exists(files.evalReportMd())).isTrue();
        String markdown = Files.readString(files.evalReportMd());
        assertThat(markdown).contains("报告纵向分析", "失败阶段分布");
        assertThat(markdown).doesNotContain("完整报告原文", "完整问诊原文");
        assertThat(Files.readString(files.badcaseAnalysisJson())).contains("TREND_ANALYSIS_ERROR");
    }
}
