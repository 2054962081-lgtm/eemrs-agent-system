package com.liu.eemrsagent.eval;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReportTrendBadCaseAttributorTest {
    private final ReportTrendBadCaseAttributor attributor = new ReportTrendBadCaseAttributor();

    @Test
    void attributesAllMainFailureStages() {
        assertStage(error("REPORT_DECRYPT_FAILED"), passing(), ReportTrendFailureStage.REPORT_DECRYPT_ERROR);
        assertStage(error("REPORT_PARSE_FAILED"), passing(), ReportTrendFailureStage.REPORT_PARSE_ERROR);
        assertStage(error("CLOUD_PAYLOAD_PRIVACY_VIOLATION"), passing(), ReportTrendFailureStage.CLOUD_PAYLOAD_PRIVACY_ERROR);
        assertStage(error("CLOUD_MODEL_FAILED"), passing(), ReportTrendFailureStage.CLOUD_MODEL_ERROR);
        assertStage(error("CLOUD_RESPONSE_INVALID"), passing(), ReportTrendFailureStage.CLOUD_RESPONSE_INVALID);
        assertStage(error("RESULT_ENCRYPT_FAILED"), passing(), ReportTrendFailureStage.RESULT_ENCRYPT_ERROR);
    }

    @Test
    void attributesMetricFailuresAndNone() {
        ReportTrendEvalResult r = passing();
        r.setTraceComplete(false);
        assertStage(ReportTrendTraceBasedEvaluatorTest.evidence(), r, ReportTrendFailureStage.TRACE_INCOMPLETE);
        r = passing();
        r.setAbnormalDetectionCorrect(false);
        assertStage(ReportTrendTraceBasedEvaluatorTest.evidence(), r, ReportTrendFailureStage.ABNORMAL_DETECTION_ERROR);
        r = passing();
        r.setTrendDirectionCorrect(false);
        assertStage(ReportTrendTraceBasedEvaluatorTest.evidence(), r, ReportTrendFailureStage.TREND_ANALYSIS_ERROR);
        r = passing();
        r.setContextLinkCorrect(false);
        assertStage(ReportTrendTraceBasedEvaluatorTest.evidence(), r, ReportTrendFailureStage.CONTEXT_FUSION_ERROR);
        r = passing();
        r.setPrivacyPass(false);
        assertStage(ReportTrendTraceBasedEvaluatorTest.evidence(), r, ReportTrendFailureStage.CLOUD_PAYLOAD_PRIVACY_ERROR);
        r = passing();
        r.setCloudResponseValid(false);
        assertStage(ReportTrendTraceBasedEvaluatorTest.evidence(), r, ReportTrendFailureStage.CLOUD_RESPONSE_INVALID);
        assertStage(ReportTrendTraceBasedEvaluatorTest.evidence(), passing(), ReportTrendFailureStage.NONE);
    }

    @Test
    void attributesContextLoadErrorAndStepFailures() {
        ReportTrendTraceEvidence evidence = ReportTrendTraceBasedEvaluatorTest.evidence();
        evidence.setSymptomTagCount(0);
        evidence.setChronicDiseaseTagCount(0);
        assertStage(evidence, passing(), ReportTrendFailureStage.CONTEXT_LOAD_ERROR);

        evidence = ReportTrendTraceBasedEvaluatorTest.evidence();
        evidence.getStepStatusMap().put("REPORT_STRUCTURING", "FAILED");
        assertStage(evidence, passing(), ReportTrendFailureStage.REPORT_PARSE_ERROR);
    }

    @Test
    void exposesIndicatorNormalizeStageValue() {
        assertThat(ReportTrendFailureStage.INDICATOR_NORMALIZE_ERROR).isNotNull();
    }

    private ReportTrendEvalResult passing() {
        ReportTrendEvalResult r = new ReportTrendTraceBasedEvaluator().evaluate(
                ReportTrendTraceBasedEvaluatorTest.evalCase(),
                ReportTrendTraceBasedEvaluatorTest.actual(),
                ReportTrendTraceBasedEvaluatorTest.evidence());
        r.setTraceComplete(true);
        return r;
    }

    private ReportTrendTraceEvidence error(String code) {
        ReportTrendTraceEvidence e = ReportTrendTraceBasedEvaluatorTest.evidence();
        e.setErrorCode(code);
        return e;
    }

    private void assertStage(ReportTrendTraceEvidence evidence, ReportTrendEvalResult result, ReportTrendFailureStage stage) {
        assertThat(attributor.attribute(ReportTrendTraceBasedEvaluatorTest.evalCase(), evidence, result).stage()).isEqualTo(stage);
    }
}
