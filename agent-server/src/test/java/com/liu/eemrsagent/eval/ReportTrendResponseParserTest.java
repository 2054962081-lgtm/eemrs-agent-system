package com.liu.eemrsagent.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReportTrendResponseParserTest {
    private final ReportTrendResponseParser parser = new ReportTrendResponseParser(new ObjectMapper());

    @Test
    void parsesNormalResponse() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("analysisId", "analysis_1");
        response.put("traceRunId", "run_1");
        response.put("status", "SUCCESS");
        response.put("doctorSummary", "医生摘要");
        response.put("patientExplanation", "患者解释");
        response.put("contextualInterpretation", "结合发热和 WBC");
        response.put("contextLinks", java.util.List.of(Map.of("symptoms", java.util.List.of("发热"), "indicators", java.util.List.of("WBC"))));
        response.put("abnormalItems", java.util.List.of(Map.of("standardCode", "WBC", "abnormalFlag", "HIGH")));
        response.put("trendItems", java.util.List.of(Map.of("code", "WBC", "trendDirection", "INCREASING")));
        response.put("followUpQuestions", java.util.List.of("最高体温是多少"));
        response.put("recommendation", Map.of("suggestedDepartment", "呼吸内科", "suggestedAction", "线下就诊"));
        ReportTrendActualResult actual = parser.parse(response);

        assertThat(actual.getDoctorSummary()).isEqualTo("医生摘要");
        assertThat(actual.getContextLinks()).hasSize(1);
        assertThat(actual.abnormalFlagsByCode()).containsEntry("WBC", "HIGH");
        assertThat(actual.trendDirectionsByCode()).containsEntry("WBC", "INCREASING");
    }

    @Test
    void toleratesMissingFieldsAndSanitizesRawIdentifiers() {
        String mobileLike = "138" + "00138000";
        String idCardLike = "11010119900101" + "1234";
        ReportTrendActualResult actual = parser.parse(Map.of(
                "status", "FAILED",
                "errorCode", "REPORT_PARSE_FAILED",
                "patientExplanation", "手机号 " + mobileLike + " 身份证 " + idCardLike
        ));

        assertThat(actual.getDoctorSummary()).isNull();
        assertThat(actual.getContextualInterpretation()).isNull();
        assertThat(actual.getContextLinks()).isEmpty();
        assertThat(actual.getAbnormalItems()).isEmpty();
        assertThat(actual.getTrendItems()).isEmpty();
        assertThat(actual.getErrorCode()).isEqualTo("REPORT_PARSE_FAILED");
        assertThat(actual.getSanitizedText()).doesNotContain(mobileLike, idCardLike);
    }
}
