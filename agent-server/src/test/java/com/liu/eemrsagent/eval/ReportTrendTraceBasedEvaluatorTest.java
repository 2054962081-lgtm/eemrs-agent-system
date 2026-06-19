package com.liu.eemrsagent.eval;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReportTrendTraceBasedEvaluatorTest {
    private final ReportTrendTraceBasedEvaluator evaluator = new ReportTrendTraceBasedEvaluator();

    @Test
    void calculatesReportTrendMetrics() {
        ReportTrendEvalResult result = evaluator.evaluate(evalCase(), actual(), evidence());

        assertThat(result.getAbnormalDetectionCorrect()).isTrue();
        assertThat(result.getTrendDirectionCorrect()).isTrue();
        assertThat(result.getContextLinkCorrect()).isTrue();
        assertThat(result.getSuggestedDepartmentCorrect()).isTrue();
        assertThat(result.getPrivacyPass()).isTrue();
        assertThat(result.getCloudResponseValid()).isTrue();
        assertThat(result.getDoctorSummaryPresent()).isTrue();
        assertThat(result.getPatientExplanationPresent()).isTrue();
        assertThat(result.getContextualInterpretationPresent()).isTrue();
        assertThat(result.getTraceComplete()).isTrue();
    }

    static ReportTrendEvalCase evalCase() {
        ReportTrendEvalCase c = new ReportTrendEvalCase();
        c.setCaseId("RT-WBC-CRP-CTX-001");
        c.setSource("test");
        c.setScenario("白细胞和 CRP 升高，结合发热咳嗽症状");
        c.setCategory("context");
        ReportTrendEvalCase.Expected e = new ReportTrendEvalCase.Expected();
        e.setExpectedAbnormalItems(List.of(new ReportTrendEvalCase.ExpectedAbnormalItem("WBC", "白细胞", "HIGH")));
        e.setExpectedTrends(List.of(new ReportTrendEvalCase.ExpectedTrend("WBC", "INCREASING")));
        e.setExpectedContextLinks(List.of(new ReportTrendEvalCase.ExpectedContextLink("symptom_lab_relation", List.of("发热"), List.of("WBC"))));
        e.setExpectedSuggestedDepartment("呼吸内科");
        e.setDoctorSummaryRequired(true);
        e.setPatientExplanationRequired(true);
        e.setContextualInterpretationRequired(true);
        e.setCloudResponseValidRequired(true);
        e.setPrivacyPassRequired(true);
        e.setTraceCompleteRequired(true);
        c.setExpected(e);
        return c;
    }

    static ReportTrendActualResult actual() {
        ReportTrendActualResult a = new ReportTrendActualResult();
        a.setAnalysisId("analysis_1");
        a.setTraceRunId("run_1");
        a.setDoctorSummary("医生摘要");
        a.setPatientExplanation("患者解释");
        a.setContextualInterpretation("结合发热和 WBC 升高");
        a.setContextLinks(List.of(Map.of("symptoms", List.of("发热"), "indicators", List.of("WBC"))));
        a.setAbnormalItems(List.of(Map.of("standardCode", "WBC", "abnormalFlag", "HIGH")));
        a.setTrendItems(List.of(Map.of("code", "WBC", "trendDirection", "INCREASING")));
        a.setFollowUpQuestions(List.of("最高体温是多少"));
        a.setSuggestedDepartment("呼吸内科");
        a.setSuggestedAction("线下就诊");
        a.setSanitizedText("医生摘要 患者解释 结合发热和 WBC");
        return a;
    }

    static ReportTrendTraceEvidence evidence() {
        ReportTrendTraceEvidence e = new ReportTrendTraceEvidence();
        e.setTraceRunId("run_1");
        for (String step : ReportTrendTraceParser.requiredSteps()) {
            e.getStepStatusMap().put(step, "SUCCESS");
        }
        e.setCloudResponseValid(true);
        e.setPayloadHash("sha256:payload");
        e.setResponseHash("sha256:response");
        e.setSymptomTagCount(1);
        return e;
    }
}
