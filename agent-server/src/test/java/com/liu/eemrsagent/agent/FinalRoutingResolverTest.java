package com.liu.eemrsagent.agent;

import com.liu.eemrsagent.rag.QuestionPlan;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FinalRoutingResolverTest {

    private final FinalRoutingResolver resolver = new FinalRoutingResolver();

    @Test
    void conditionalEmergencyAdviceDoesNotOverrideMediumPlanUrgency() {
        FinalRoutingResolver.FinalRoutingDecision decision = resolver.resolve(
                """
                        【推荐科室】
                        优先建议：消化内科
                        备选科室：普外科
                        【注意事项】
                        如出现腹痛突然剧烈加重、黑便或便血，请立即前往急诊，不要等待门诊。
                        """,
                plan("medium", "尽快就医", List.of())
        );

        assertThat(decision.urgency()).isEqualTo("urgent");
        assertThat(decision.urgencySource()).isEqualTo("QUESTION_PLAN");
        assertThat(decision.urgencyOverrideReason()).isEqualTo("CONDITIONAL_EMERGENCY_ADVICE_IGNORED");
        assertThat(decision.recommendedDepartment()).isEqualTo("消化内科");
    }

    @Test
    void activeRedFlagKeepsEmergencyUrgency() {
        FinalRoutingResolver.FinalRoutingDecision decision = resolver.resolve(
                "建议尽快就医。",
                plan("high", "emergency", List.of("GI bleeding"))
        );

        assertThat(decision.urgency()).isEqualTo("emergency");
        assertThat(decision.urgencySource()).isEqualTo("QUESTION_PLAN");
        assertThat(decision.activatedRedFlagCount()).isEqualTo(1);
    }

    @Test
    void necessaryEmergencyTextIsNotDirectEmergencyCandidate() {
        String reply = "建议尽快线下就医，必要时急诊。";

        assertThat(resolver.parseTextUrgencyCandidate(reply)).isEqualTo("urgent");
    }

    @Test
    void plannedUrgencyFallsBackWhenModelTextHasNoUrgencySignal() {
        FinalRoutingResolver.FinalRoutingDecision decision = resolver.resolve(
                "【推荐科室】\n优先建议：消化内科",
                plan("medium", "尽快就医", List.of())
        );

        assertThat(decision.urgency()).isEqualTo("urgent");
        assertThat(decision.urgencySource()).isEqualTo("QUESTION_PLAN");
    }

    @Test
    void specialPopulationWithoutActiveRedFlagDoesNotBecomeEmergency() {
        FinalRoutingResolver.FinalRoutingDecision decision = resolver.resolve(
                """
                        【推荐科室】
                        建议优先就诊呼吸内科。
                        【提醒】
                        孕期咳嗽如出现呼吸困难或咯血，请及时急诊。
                        """,
                plan("high", "立即就医", List.of())
        );

        assertThat(decision.urgency()).isEqualTo("urgent");
        assertThat(decision.activatedRedFlagCount()).isZero();
    }

    @Test
    void normalPlanDoesNotAllowModelTextToEscalateEmergencyWithoutEvidence() {
        FinalRoutingResolver.FinalRoutingDecision decision = resolver.resolve(
                """
                        【推荐科室】
                        建议优先就诊呼吸内科。
                        【当前就医建议】
                        当前建议立即前往急诊或拨打120。
                        """,
                plan("normal", "普通门诊", List.of())
        );

        assertThat(decision.urgency()).isEqualTo("normal");
        assertThat(decision.urgencySource()).isEqualTo("QUESTION_PLAN");
        assertThat(decision.urgencyOverrideReason()).isEqualTo("MODEL_EMERGENCY_WITHOUT_STRUCTURED_EVIDENCE_IGNORED");
    }

    @Test
    void activeRedFlagCanEscalateNormalTextToEmergencyWithReason() {
        FinalRoutingResolver.FinalRoutingDecision decision = resolver.resolve(
                "建议线下就医。",
                plan("high", "普通门诊", List.of("心悸伴晕厥"))
        );

        assertThat(decision.urgency()).isEqualTo("emergency");
        assertThat(decision.urgencyOverrideReason()).isEqualTo("PLAN_EMERGENCY_OR_ACTIVE_RED_FLAG");
    }

    @Test
    void plannedPrimaryDepartmentIsNotOverriddenByAlternativeText() {
        FinalRoutingResolver.FinalRoutingDecision decision = resolver.resolve(
                """
                        【推荐科室】
                        优先建议：普外科
                        备选科室：急诊科
                        """,
                plan("medium", "尽快就医", List.of())
        );

        assertThat(decision.recommendedDepartment()).isEqualTo("消化内科");
        assertThat(decision.departmentSource()).isEqualTo("QUESTION_PLAN");
    }

    private QuestionPlan plan(String risk, String urgency, List<String> activatedRedFlags) {
        return new QuestionPlan(
                risk,
                List.of("消化内科", "普外科"),
                urgency,
                List.of("腹痛开始时间、部位、性质和程度"),
                List.of(),
                List.of(),
                List.of("起病时间", "持续时间", "用药史"),
                List.of("主诉", "现病史", "用药史"),
                List.of("腹痛问诊模板"),
                List.of("symptom_inquiry"),
                List.of("GI bleeding"),
                activatedRedFlags,
                List.of(),
                List.of(),
                "No patient-positive red flag evidence",
                "Digestive department primary"
        );
    }
}
