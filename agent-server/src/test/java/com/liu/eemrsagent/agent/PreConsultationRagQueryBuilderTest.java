package com.liu.eemrsagent.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PreConsultationRagQueryBuilderTest {

    private final PreConsultationRagQueryBuilder builder = new PreConsultationRagQueryBuilder();

    @Test
    void preservesChiefComplaintWhenLaterRoundOnlyContainsMedicationDetail() {
        PreConsultationRagQueryBuilder.RagQueryContext context = builder.build(
                "使用过非处方退热止痛药2，按说明书短期使用一次",
                List.of(
                        new PreConsultationRequest.Message("user", "我腹痛，中等程度，逐渐加重"),
                        new PreConsultationRequest.Message("assistant", "请补充持续时间和用药情况。"),
                        new PreConsultationRequest.Message("user", "持续1天"),
                        new PreConsultationRequest.Message("assistant", "还用过什么药吗？")
                )
        );

        assertThat(context.query()).contains("腹痛", "中等程度", "持续1天", "非处方退热止痛药");
        assertThat(context.query()).doesNotContain("请补充");
        assertThat(context.chiefComplaintPresent()).isTrue();
        assertThat(context.resolvedSlotNames()).contains("duration", "medication_detail");
        assertThat(context.querySource()).isEqualTo("chief_complaint_plus_patient_facts");
    }

    @Test
    void ignoresAssistantFeedbackAndLowInformationPatientReplies() {
        PreConsultationRagQueryBuilder.RagQueryContext context = builder.build(
                "好的",
                List.of(
                        new PreConsultationRequest.Message("user", "胸痛半小时，伴胸闷"),
                        new PreConsultationRequest.Message("assistant", "可能需要急诊评估"),
                        new PreConsultationRequest.Message("user", "暂时没有")
                )
        );

        assertThat(context.query()).contains("胸痛半小时", "胸闷");
        assertThat(context.query()).doesNotContain("急诊评估", "暂时没有", "好的");
        assertThat(context.latestMessageIgnored()).isTrue();
    }

    @Test
    void usesCurrentInputAsChiefComplaintWhenHistoryIsEmpty() {
        PreConsultationRagQueryBuilder.RagQueryContext context = builder.build("咳嗽发热两天", List.of());

        assertThat(context.query()).contains("咳嗽发热两天");
        assertThat(context.querySource()).isEqualTo("chief_complaint");
        assertThat(context.latestMessageUsed()).isTrue();
    }
}
