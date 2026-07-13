package com.liu.eemrsagent.reporttrend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liu.eemrsagent.trace.TraceRedactor;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportTrendComponentTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TraceRedactor traceRedactor = new TraceRedactor();
    private final LabIndicatorDictionary dictionary = new LabIndicatorDictionary(objectMapper);

    @Test
    void normalizesIndicatorAliasAndDetectsAbnormalRange() {
        LocalReportStructuringService service = new LocalReportStructuringService(dictionary, traceRedactor);

        StructuredLabReport report = service.structure("r1", LocalDate.parse("2026-05-01"), "LAB", """
                白细胞计数 12.1 10^9/L 参考 3.5-9.5
                CRP 16 mg/L 参考 <8
                """);

        assertThat(report.items()).hasSize(2);
        assertThat(report.items().get(0).standardCode()).isEqualTo("WBC");
        assertThat(report.items().get(0).abnormalFlag()).isEqualTo(AbnormalFlag.HIGH);
        assertThat(report.items().get(1).standardCode()).isEqualTo("CRP");
        assertThat(report.items().get(1).abnormalFlag()).isEqualTo(AbnormalFlag.HIGH);
    }

    @Test
    void calculatesIncreasingTrendAndConsecutiveAbnormalCount() {
        ReportTrendProperties properties = new ReportTrendProperties();
        TrendAnalysisService service = new TrendAnalysisService(properties);
        List<StructuredLabReport> reports = List.of(
                report("r1", "2026-01-01", value("WBC", "白细胞", "8.0", "3.5", "9.5")),
                report("r2", "2026-02-01", value("WBC", "白细胞", "10.5", "3.5", "9.5")),
                report("r3", "2026-03-01", value("WBC", "白细胞", "12.0", "3.5", "9.5"))
        );

        TrendItem trend = service.analyze(reports).get(0);

        assertThat(trend.trendDirection()).isEqualTo(TrendDirection.INCREASING);
        assertThat(trend.abnormalCount()).isEqualTo(2);
        assertThat(trend.consecutiveAbnormalCount()).isEqualTo(2);
    }

    @Test
    void blocksForbiddenCloudPayloadFieldsAndIdentifiers() {
        CloudPayloadPrivacyGuard guard = new CloudPayloadPrivacyGuard(objectMapper);

        assertThatThrownBy(() -> guard.assertSafe(Map.of("patientId", "p001")))
                .isInstanceOf(ReportTrendException.class)
                .hasMessageContaining("Forbidden");
        String mobileLike = "138" + "00138000";
        assertThatThrownBy(() -> guard.assertSafe(Map.of("coarse_patient_context", mobileLike)))
                .isInstanceOf(ReportTrendException.class)
                .hasMessageContaining("identifier");
    }

    @Test
    void parsesCloudJsonAndRejectsInvalidResponse() {
        CloudReportAnalysisClient client = new CloudReportAnalysisClient(null, objectMapper);

        CloudReportResponse response = client.parse("""
                {"doctorSummary":"WBC 升高，建议结合感染相关症状评估","patientExplanation":"白细胞偏高可提示炎症或感染，需要结合症状判断","keyAbnormalItems":[],"riskNotes":[],"followUpQuestions":["是否发热"],"suggestedDepartment":"内科","suggestedAction":"建议结合症状线下就诊或复查"}
                """);

        assertThat(response.doctorSummary()).contains("WBC");
        assertThatThrownBy(() -> client.parse("{not-json}"))
                .isInstanceOf(ReportTrendException.class);
    }

    private StructuredLabReport report(String id, String date, LabIndicatorItem item) {
        return new StructuredLabReport(id, LocalDate.parse(date), "LAB", List.of(item));
    }

    private LabIndicatorItem value(String code, String name, String value, String low, String high) {
        java.math.BigDecimal current = new java.math.BigDecimal(value);
        java.math.BigDecimal referenceLow = new java.math.BigDecimal(low);
        java.math.BigDecimal referenceHigh = new java.math.BigDecimal(high);
        AbnormalFlag flag = current.compareTo(referenceLow) < 0 ? AbnormalFlag.LOW
                : current.compareTo(referenceHigh) > 0 ? AbnormalFlag.HIGH : AbnormalFlag.NORMAL;
        return new LabIndicatorItem(name, code, name, current, "10^9/L", referenceLow, referenceHigh, flag);
    }
}
