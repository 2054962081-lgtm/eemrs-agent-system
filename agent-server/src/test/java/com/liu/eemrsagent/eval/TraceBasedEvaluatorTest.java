package com.liu.eemrsagent.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class TraceBasedEvaluatorTest {
    private final TraceBasedEvaluator evaluator = new TraceBasedEvaluator(new ObjectMapper());

    @Test
    void calculatesCoreMetricsFromTrace() {
        EvalResult result = evaluator.evaluate(EvalTestFixtures.chestPainCase(), EvalTestFixtures.passingTrace());

        assertThat(result.getDepartmentCorrect()).isTrue();
        assertThat(result.getPrimaryDepartmentCorrect()).isTrue();
        assertThat(result.getMustAskCoverage()).isEqualTo(1.0d);
        assertThat(result.getRedFlagHitRate()).isEqualTo(1.0d);
        assertThat(result.isFollowUpCorrect()).isTrue();
        assertThat(result.getToolCallCorrect()).isEqualTo("true");
        assertThat(result.isTraceComplete()).isTrue();
        assertThat(result.getRunId()).isEqualTo("run-test-1");
        assertThat(result.getLatencyMs()).isEqualTo(100);
        assertThat(result.getTotalTokens()).isEqualTo(321);
    }

    @Test
    void detectsDuplicatedSequenceAsIncompleteTrace() {
        EvalTraceDetail detail = EvalTestFixtures.passingTrace();
        detail.getSteps().get(1).setSequenceNo(1);

        assertThat(evaluator.isTraceComplete(detail)).isFalse();
    }

    @Test
    void toleratesMissingOptionalToolCallStep() {
        EvalTraceDetail detail = EvalTestFixtures.passingTrace();
        detail.setToolCalls(new ArrayList<>());

        EvalResult result = evaluator.evaluate(EvalTestFixtures.chestPainCase(), detail);

        assertThat(result.getToolCallActual()).isEqualTo("false");
    }
}
