package com.liu.eemrsagent.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReportTrendTraceParserTest {
    private final ReportTrendTraceParser parser = new ReportTrendTraceParser(new ObjectMapper());

    @Test
    void extractsKeyEvidenceFromTraceDetail() {
        EvalTraceDetail detail = detail(false, true);

        ReportTrendTraceEvidence evidence = parser.parse(detail);

        assertThat(evidence.getStepStatusMap()).containsKeys("REPORT_ANALYSIS_REQUEST", "TREND_ANALYSIS", "CONTEXT_QUERY");
        assertThat(evidence.getAbnormalCount()).isEqualTo(2);
        assertThat(evidence.getTrendItemCount()).isEqualTo(2);
        assertThat(evidence.getSymptomTagCount()).isEqualTo(2);
        assertThat(evidence.getPrivacyGuardStatus()).isEqualTo("SUCCESS");
        assertThat(parser.isTraceComplete(detail)).isTrue();
    }

    @Test
    void detectsMissingStepAndDuplicatedSequence() {
        assertThat(parser.isTraceComplete(detail(false, false))).isFalse();
        assertThat(parser.parse(detail(true, true)).isSequenceDuplicate()).isTrue();
    }

    private EvalTraceDetail detail(boolean duplicateSequence, boolean complete) {
        EvalTraceDetail detail = new EvalTraceDetail();
        detail.setRun(new LinkedHashMap<>(Map.of("runId", "run_1", "totalLatencyMs", 100, "totalTokens", 20)));
        java.util.ArrayList<EvalTraceStep> steps = new java.util.ArrayList<>();
        int sequence = 1;
        for (String type : ReportTrendTraceParser.requiredSteps()) {
            if (!complete && "CLOUD_MODEL_RESPONSE".equals(type)) continue;
            EvalTraceStep step = new EvalTraceStep();
            step.setSequenceNo(duplicateSequence ? 1 : sequence++);
            step.setStepType(type);
            step.setStatus("SUCCESS");
            step.setResponsePayloadJson(metadata(type));
            step.setMetadataJson(metadata(type));
            steps.add(step);
        }
        detail.setSteps(steps);
        return detail;
    }

    private String metadata(String type) {
        return switch (type) {
            case "ABNORMAL_DETECTION" -> "{\"abnormal_count\":2}";
            case "TREND_ANALYSIS" -> "{\"trend_item_count\":2}";
            case "PRECONSULTATION_CONTEXT_LOAD" -> "{\"context_available\":true,\"symptom_tag_count\":2}";
            case "LONG_TERM_HEALTH_CONTEXT_LOAD" -> "{\"context_available\":true,\"chronic_disease_tag_count\":1}";
            case "CLOUD_PAYLOAD_BUILD" -> "{\"payload_hash\":\"sha256:x\"}";
            case "CLOUD_RESPONSE_VALIDATE" -> "{\"cloud_response_valid\":true}";
            default -> "{}";
        };
    }
}
