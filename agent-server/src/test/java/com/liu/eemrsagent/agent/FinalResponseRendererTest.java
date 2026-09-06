package com.liu.eemrsagent.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FinalResponseRendererTest {

    private final FinalResponseRenderer renderer = new FinalResponseRenderer();

    @Test
    void structuredRoutingOverridesPrimaryRecommendationButKeepsConditionalEmergencyAdvice() {
        FinalRoutingResolver.FinalRoutingDecision decision = decision("消化内科", "urgent", "QUESTION_PLAN");
        String llmReply = """
                【症状摘要】
                腹痛1天，中等程度，逐渐加重。

                【推荐科室】
                优先建议：急诊科
                备选科室：消化内科、普外科

                【就医建议】
                建议尽快就医。

                【注意事项】
                如果出现剧烈腹痛、黑便、晕厥，请立即前往急诊或拨打120。

                【重要提示】
                本回复仅供预问诊参考，不能替代医生诊断。
                """;

        FinalResponseRenderer.RenderResult result = renderer.render(llmReply, decision);

        assertThat(result.reply()).contains("【推荐科室】\n建议优先就诊消化内科。");
        assertThat(result.reply()).contains("【当前就医建议】\n建议尽快线下就医。");
        assertThat(result.reply()).contains("如果出现剧烈腹痛、黑便、晕厥，请立即前往急诊或拨打120。");
        assertThat(result.renderedPrimaryDepartment()).isEqualTo("消化内科");
        assertThat(result.renderedCurrentUrgency()).isEqualTo("urgent");
        assertThat(result.conditionalEmergencyAdvicePresent()).isTrue();
        assertThat(result.routingConsistency()).isTrue();
        assertThat(result.reply()).doesNotContain("优先建议：急诊科");
    }

    @Test
    void emergencyRoutingRemainsEmergencyWhenStructuredDecisionIsEmergency() {
        FinalRoutingResolver.FinalRoutingDecision decision = decision("急诊科", "emergency", "QUESTION_PLAN");

        FinalResponseRenderer.RenderResult result = renderer.render("【症状摘要】呕血黑便。", decision);

        assertThat(result.reply()).contains("建议优先就诊急诊科。");
        assertThat(result.reply()).contains("当前建议立即前往急诊或拨打120。");
        assertThat(result.renderedPrimaryDepartment()).isEqualTo("急诊科");
        assertThat(result.renderedCurrentUrgency()).isEqualTo("emergency");
        assertThat(result.routingConsistency()).isTrue();
    }

    @Test
    void conditionalEmergencyOutsideCurrentUrgencyDoesNotBreakConsistency() {
        FinalRoutingResolver.FinalRoutingDecision decision = decision("消化内科", "urgent", "QUESTION_PLAN");

        FinalResponseRenderer.RenderResult result = renderer.render("""
                【注意事项】
                如出现呕血黑便等危险信号，请立即急诊。
                """, decision);

        assertThat(result.renderedCurrentUrgency()).isEqualTo("urgent");
        assertThat(result.conditionalEmergencyAdvicePresent()).isTrue();
        assertThat(result.routingConsistency()).isTrue();
    }

    @Test
    void terminalRenderRemovesFollowUpQuestionSections() {
        FinalRoutingResolver.FinalRoutingDecision decision = decision("呼吸内科", "normal", "QUESTION_PLAN");

        FinalResponseRenderer.RenderResult result = renderer.render("""
                【症状摘要】
                咳嗽1周。

                【还需要确认】
                1. 有没有发热？

                【补充关键问题】
                1. 请补充用药史。

                【推荐科室】
                优先建议：呼吸内科
                """, decision, true);

        assertThat(result.reply()).doesNotContain("还需要确认");
        assertThat(result.reply()).doesNotContain("补充关键问题");
        assertThat(result.reply()).contains("【推荐科室】");
    }

    private FinalRoutingResolver.FinalRoutingDecision decision(String department, String urgency, String source) {
        return new FinalRoutingResolver.FinalRoutingDecision(
                department,
                urgency,
                source,
                "QUESTION_PLAN",
                "QUESTION_PLAN_PRECEDENCE",
                "emergency",
                urgency,
                "medium",
                "emergency".equals(urgency) ? 1 : 0
        );
    }
}
