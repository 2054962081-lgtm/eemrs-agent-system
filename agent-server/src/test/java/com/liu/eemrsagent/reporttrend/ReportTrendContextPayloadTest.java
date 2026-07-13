package com.liu.eemrsagent.reporttrend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liu.eemrsagent.trace.NoopTraceRecorder;
import com.liu.eemrsagent.trace.TraceRedactor;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportTrendContextPayloadTest {

    @Test
    void missingContextDoesNotFailAndPayloadContainsUnavailableContext() {
        ReportTrendContext context = ReportTrendContext.empty();
        CloudPayloadBuilder builder = new CloudPayloadBuilder();

        Map<String, Object> payload = builder.build(request(false, false), List.of(report()), List.of(), context);

        assertThat(payload).containsKeys("symptom_context_summary", "health_context_summary", "triage_context_summary");
        assertThat(payload.toString()).doesNotContain("patient-1");
    }

    @Test
    void contextPayloadUsesOnlyTagsNotRawDialogueOrPatientId() {
        ReportTrendContext context = new ReportTrendContext(
                Map.of("available", true, "chief_complaint_tags", List.of("发热", "咳嗽"), "symptom_duration_bucket", "1-3天", "red_flag_tags", List.of(), "current_symptom_category", "respiratory"),
                Map.of("available", true, "chronic_disease_tags", List.of("糖尿病"), "special_population_tags", List.of()),
                Map.of("available", true, "recommended_department", "呼吸内科", "registration_suggested", true),
                new ContextUsed(true, true, true),
                List.of("发热", "咳嗽"),
                List.of("糖尿病"),
                "呼吸内科"
        );

        Map<String, Object> payload = new CloudPayloadBuilder().build(request(true, true), List.of(report()), List.of(), context);
        new CloudPayloadPrivacyGuard(new ObjectMapper()).assertSafe(payload);

        assertThat(payload.toString()).contains("发热", "糖尿病", "呼吸内科");
        assertThat(payload.toString()).doesNotContain("patientId", "patient-1", "完整问诊");
    }

    @Test
    void privacyGuardBlocksRawContextAndIdentifiers() {
        CloudPayloadPrivacyGuard guard = new CloudPayloadPrivacyGuard(new ObjectMapper());

        assertThatThrownBy(() -> guard.assertSafe(Map.of(
                "analysis_task", "x",
                "raw_dialogue", "完整问诊原文"
        ))).isInstanceOf(ReportTrendException.class);
        String mobileLike = "138" + "00138000";
        assertThatThrownBy(() -> guard.assertSafe(Map.of(
                "analysis_task", "x",
                "symptom_context_summary", Map.of("available", true, "chief_complaint_tags", List.of(mobileLike))
        ))).isInstanceOf(ReportTrendException.class);
    }

    @Test
    void cloudResponseParsesContextualInterpretationAndLinks() {
        CloudReportAnalysisClient client = new CloudReportAnalysisClient(null, new ObjectMapper());

        CloudReportResponse response = client.parse("""
                {"doctorSummary":"炎症指标升高","patientExplanation":"指标需要结合症状判断","contextualInterpretation":"结合发热和咳嗽，提示需关注感染相关线索","keyAbnormalItems":[],"contextLinks":[{"type":"symptom_lab_relation","symptoms":["发热"],"indicators":["WBC"],"note":"需结合查体判断"}],"riskNotes":[],"followUpQuestions":["最高体温是多少"],"suggestedDepartment":"呼吸内科","suggestedAction":"建议线下就诊"}
                """);

        assertThat(response.contextualInterpretation()).contains("发热");
        assertThat(response.contextLinks()).hasSize(1);
    }

    @Test
    void contextServiceLoadsDraftTagsWithoutRawText() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(any(String.class), eq(Integer.class), eq("agent_medical_record_draft"))).thenReturn(1);
        when(jdbcTemplate.queryForObject(any(String.class), eq(Integer.class), eq("user_long_term_memory"))).thenReturn(0);
        when(jdbcTemplate.queryForList(any(String.class), any(Object[].class))).thenReturn(List.of(Map.of(
                "chief_complaint", "发热咳嗽3天",
                "present_illness_history", "伴咽痛",
                "recommended_department", "呼吸内科",
                "consultation_summary", "无高热不退",
                "record_json", "{}"
        )));
        ReportTrendContextService service = new ReportTrendContextService(jdbcTemplate, new NoopTraceRecorder(), new TraceRedactor());

        ReportTrendContext context = service.load(request(true, false));

        assertThat(context.contextUsed().preconsultation()).isTrue();
        assertThat(context.symptomTags()).contains("发热", "咳嗽");
        assertThat(context.triageContextSummary().toString()).contains("呼吸内科");
    }

    private ReportTrendAnalysisRequest request(boolean preconsultation, boolean longTerm) {
        return new ReportTrendAnalysisRequest("patient-1", "session-1", preconsultation, longTerm, "LAB",
                LocalDate.parse("2026-01-01"), LocalDate.parse("2026-06-30"), List.of("WBC"), "DOCTOR_AND_PATIENT");
    }

    private StructuredLabReport report() {
        return new StructuredLabReport("r1", LocalDate.parse("2026-01-01"), "LAB", List.of());
    }
}
