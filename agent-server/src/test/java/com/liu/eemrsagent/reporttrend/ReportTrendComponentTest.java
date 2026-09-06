package com.liu.eemrsagent.reporttrend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liu.eemrsagent.llm.LlmClientFactory;
import com.liu.eemrsagent.llm.LlmException;
import com.liu.eemrsagent.trace.TraceRedactor;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
        assertThat(trend.pointCount()).isEqualTo(3);
        assertThat(trend.firstDate()).isEqualTo(LocalDate.parse("2026-01-01"));
        assertThat(trend.latestDate()).isEqualTo(LocalDate.parse("2026-03-01"));
        assertThat(trend.firstValue()).isEqualByComparingTo("8.0");
        assertThat(trend.abnormalCount()).isEqualTo(2);
        assertThat(trend.consecutiveAbnormalCount()).isEqualTo(2);
    }

    @Test
    void calculatesFullSeriesNumericChangeForR001StyleTrend() {
        TrendAnalysisService service = new TrendAnalysisService(new ReportTrendProperties());
        List<StructuredLabReport> reports = List.of(
                report("r3", "2026-03-11", value("WBC", "白细胞", "16", "3.5", "20.0")),
                report("r1", "2026-01-11", value("WBC", "白细胞", "8", "3.5", "20.0")),
                report("r2", "2026-02-11", value("WBC", "白细胞", "12", "3.5", "20.0"))
        );

        TrendItem trend = service.analyze(reports).get(0);

        assertThat(trend.code()).isEqualTo("WBC");
        assertThat(trend.unit()).isEqualTo("10^9/L");
        assertThat(trend.pointCount()).isEqualTo(3);
        assertThat(trend.firstDate()).isEqualTo(LocalDate.parse("2026-01-11"));
        assertThat(trend.latestDate()).isEqualTo(LocalDate.parse("2026-03-11"));
        assertThat(trend.firstValue()).isEqualByComparingTo("8");
        assertThat(trend.previousValue()).isEqualByComparingTo("12");
        assertThat(trend.latestValue()).isEqualByComparingTo("16");
        assertThat(trend.changeAbsolute()).isEqualByComparingTo("8");
        assertThat(trend.changePercent()).isEqualByComparingTo("100.00");
        assertThat(trend.trendDirection()).isEqualTo(TrendDirection.INCREASING);
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
    void cloudPayloadRemovesPiiLikeNameKeyAndKeepsMedicalFields() {
        CloudPayloadBuilder builder = new CloudPayloadBuilder();
        CloudPayloadPrivacyGuard guard = new CloudPayloadPrivacyGuard(objectMapper);
        ReportTrendAnalysisRequest request = new ReportTrendAnalysisRequest("patient-1", "session-1", false, false, "LAB",
                LocalDate.parse("2026-01-01"), LocalDate.parse("2026-03-01"), List.of("WBC"), "STRUCTURED");
        List<StructuredLabReport> reports = List.of(
                report("r1", "2026-01-01", value("WBC", "白细胞", "8.0", "3.5", "9.5")),
                report("r2", "2026-03-01", value("WBC", "白细胞", "12.0", "3.5", "9.5"))
        );
        List<TrendItem> trends = new TrendAnalysisService(new ReportTrendProperties()).analyze(reports);

        Map<String, Object> payload = builder.build(request, reports, trends, ReportTrendContext.empty());

        guard.assertSafe(payload);
        assertThat(objectMapper.valueToTree(payload).findValues("name")).isEmpty();
        assertThat(payload.toString()).contains("indicator_name", "value", "unit", "reference_low", "reference_high", "trend_direction");
    }

    @Test
    void privacyGuardStillRejectsInjectedNameField() {
        CloudPayloadPrivacyGuard guard = new CloudPayloadPrivacyGuard(objectMapper);
        Map<String, Object> injected = new LinkedHashMap<>();
        injected.put("analysis_task", "LAB_REPORT_TREND_SUMMARY");
        injected.put("name", "张三");

        assertThatThrownBy(() -> guard.assertSafe(injected))
                .isInstanceOf(ReportTrendException.class)
                .hasMessageContaining("name");
    }

    @Test
    void parsesCloudJsonAndRejectsInvalidResponse() {
        CloudReportAnalysisClient client = new CloudReportAnalysisClient(null, objectMapper);

        CloudReportResponse response = client.parse("""
                {"doctorSummary":"WBC 升高，建议结合感染相关症状评估","patientExplanation":"白细胞偏高可提示炎症或感染，需要结合症状判断","contextualInterpretation":"结合发热症状判断","keyAbnormalItems":[],"contextLinks":[{"type":"symptom_lab_relation","symptoms":["发热"],"indicators":["WBC"],"note":"需结合查体判断"}],"riskNotes":["如持续高热需及时就医"],"followUpQuestions":["是否发热"],"suggestedDepartment":"内科","suggestedAction":"建议结合症状线下就诊或复查"}
                """);

        assertThat(response.doctorSummary()).contains("WBC");
        assertThat(response.contextLinks()).hasSize(1);
        assertThat(response.contextLinks().get(0).symptoms()).containsExactly("发热");
        assertThat(response.riskNotes()).containsExactly("如持续高热需及时就医");
        assertThatThrownBy(() -> client.parse("{not-json}"))
                .isInstanceOf(ReportTrendException.class)
                .extracting("errorCode")
                .isEqualTo(ReportTrendErrorCode.CLOUD_RESPONSE_JSON_INVALID);
        CloudReportResponse schemaIncomplete = client.parse("""
                {"doctorSummary":"","patientExplanation":"","keyAbnormalItems":[],"contextLinks":[],"riskNotes":[],"followUpQuestions":[],"suggestedDepartment":"","suggestedAction":""}
                """);
        assertThat(schemaIncomplete.doctorSummary()).isEmpty();
        assertThatThrownBy(() -> client.parse("""
                {"doctorSummary":"ok","patientExplanation":"ok","keyAbnormalItems":"not-array","contextLinks":"not-array","riskNotes":"not-array","followUpQuestions":"not-array","suggestedDepartment":"内科","suggestedAction":"复查"}
                """))
                .isInstanceOf(ReportTrendException.class)
                .extracting("errorCode")
                .isEqualTo(ReportTrendErrorCode.CLOUD_RESPONSE_SCHEMA_MISMATCH);
    }

    @Test
    void separatesJsonSyntaxParseFromSchemaValidation() {
        CloudReportAnalysisClient client = new CloudReportAnalysisClient(null, objectMapper);
        var json = client.parseJson("""
                {"doctorSummary":"ok","patientExplanation":"ok","keyAbnormalItems":"not-array","contextLinks":"not-array","riskNotes":"not-array","followUpQuestions":"not-array","suggestedDepartment":"内科","suggestedAction":"复查"}
                """);

        assertThat(json.get("contextLinks").isTextual()).isTrue();
        assertThatThrownBy(() -> client.toResponse(json))
                .isInstanceOf(ReportTrendException.class)
                .extracting("errorCode")
                .isEqualTo(ReportTrendErrorCode.CLOUD_RESPONSE_SCHEMA_MISMATCH);
    }

    @Test
    void parsesJsonFencedCloudResponseButRejectsProseWrappedJson() {
        CloudReportAnalysisClient client = new CloudReportAnalysisClient(null, objectMapper);

        var fenced = client.parseJson("""
                ```json
                {"doctorSummary":"ok","patientExplanation":"ok","keyAbnormalItems":[],"contextLinks":[],"riskNotes":[],"followUpQuestions":[],"suggestedDepartment":"内科","suggestedAction":"复查"}
                ```
                """);

        assertThat(fenced.get("doctorSummary").asText()).isEqualTo("ok");
        assertThatThrownBy(() -> client.parseJson("""
                下面是结果：
                {"doctorSummary":"ok","patientExplanation":"ok","keyAbnormalItems":[],"contextLinks":[],"riskNotes":[],"followUpQuestions":[],"suggestedDepartment":"内科","suggestedAction":"复查"}
                """))
                .isInstanceOf(ReportTrendException.class)
                .extracting("errorCode")
                .isEqualTo(ReportTrendErrorCode.CLOUD_RESPONSE_JSON_INVALID);
    }

    @Test
    void cloudOutputContractDeclaresCanonicalArrayShapes() {
        String contract = CloudReportAnalysisClient.outputContract();

        assertThat(contract).contains("\"contextLinks\": [");
        assertThat(contract).contains("\"symptoms\": [\"string\"]");
        assertThat(contract).contains("\"indicators\": [\"string\"]");
        assertThat(contract).contains("\"riskNotes\": [\"string\"]");
        assertThat(contract).contains("\"followUpQuestions\": [\"string\"]");
    }

    @Test
    void classifiesCloudTimeoutAndHttpFailuresWithoutRetrying() {
        LlmClientFactory llm = mock(LlmClientFactory.class);
        when(llm.chatForPurpose(eq("report_trend_cloud_analysis"), any()))
                .thenThrow(new LlmException("DeepSeek 响应超时，请稍后重试。"))
                .thenThrow(new LlmException("DeepSeek 服务暂时不可用，请稍后重试。 502"));
        CloudReportAnalysisClient client = new CloudReportAnalysisClient(llm, objectMapper);

        assertThatThrownBy(() -> client.request(Map.of("analysis_task", "unit")))
                .isInstanceOf(ReportTrendException.class)
                .extracting("errorCode")
                .isEqualTo(ReportTrendErrorCode.CLOUD_READ_TIMEOUT);
        assertThatThrownBy(() -> client.request(Map.of("analysis_task", "unit")))
                .isInstanceOf(ReportTrendException.class)
                .extracting("errorCode")
                .isEqualTo(ReportTrendErrorCode.CLOUD_HTTP_ERROR);
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
