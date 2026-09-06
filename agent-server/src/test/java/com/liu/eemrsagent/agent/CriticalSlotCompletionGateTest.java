package com.liu.eemrsagent.agent;

import com.liu.eemrsagent.rag.QuestionPlan;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CriticalSlotCompletionGateTest {

    private final CriticalSlotCompletionGate gate = new CriticalSlotCompletionGate();

    @Test
    void unresolvedMedicationDetailBlocksRecommendationBeforeMaxRounds() {
        PreConsultationRequest request = request("腹痛持续1天，中等疼痛，没有黑便便血。");

        CriticalSlotCompletionGate.Evaluation evaluation = gate.evaluate(
                request,
                request.question(),
                "【推荐科室】\n优先建议：消化内科\n【就医建议】建议尽快线下就医。",
                planWithDurationAndMedication(),
                "quick",
                2,
                true
        );

        assertThat(evaluation.mayFinish()).isFalse();
        assertThat(evaluation.terminalReason()).isEqualTo("CRITICAL_SLOT_UNRESOLVED");
        assertThat(evaluation.unresolvedSlotNames()).contains("medication_detail");
        assertThat(evaluation.unresolvedQuestions()).anyMatch(question ->
                question.contains("具体药名") && question.contains("用法用量"));
        assertThat(evaluation.slots())
                .filteredOn(slot -> slot.name().equals("medication_detail"))
                .singleElement()
                .satisfies(slot -> {
                    assertThat(slot.asked()).isFalse();
                    assertThat(slot.resolved()).isFalse();
                });
    }

    @Test
    void medicationHistoryQuestionAloneDoesNotCountAsMedicationDetailAsked() {
        PreConsultationRequest request = request("腹痛持续1天。");

        CriticalSlotCompletionGate.Evaluation evaluation = gate.evaluate(
                request,
                request.question(),
                "近期是否在服用阿司匹林、华法林等抗凝药物？",
                planWithDurationAndMedication(),
                "quick",
                2,
                false
        );

        assertThat(evaluation.slots())
                .filteredOn(slot -> slot.name().equals("medication_detail"))
                .singleElement()
                .extracting(CriticalSlotCompletionGate.SlotStatus::asked)
                .isEqualTo(false);
    }

    @Test
    void explicitMedicationDetailResolvesSlot() {
        PreConsultationRequest request = request(
                "腹痛持续1天，中等疼痛，吃了非处方退热止痛药，按说明书服用了一次。");

        CriticalSlotCompletionGate.Evaluation evaluation = gate.evaluate(
                request,
                request.question(),
                "【推荐科室】\n优先建议：消化内科",
                planWithDurationAndMedication(),
                "quick",
                2,
                true
        );

        assertThat(evaluation.mayFinish()).isTrue();
        assertThat(evaluation.terminalReason()).isEqualTo("CRITICAL_RESOLVED");
        assertThat(evaluation.slots())
                .filteredOn(slot -> slot.name().equals("medication_detail"))
                .singleElement()
                .satisfies(slot -> {
                    assertThat(slot.disclosed()).isTrue();
                    assertThat(slot.resolved()).isTrue();
                });
    }

    @Test
    void unknownMedicationNameIsLegalPatientTerminal() {
        PreConsultationRequest request = request("腹痛持续1天，中等疼痛，吃过药，但不知道药名。");

        CriticalSlotCompletionGate.Evaluation evaluation = gate.evaluate(
                request,
                request.question(),
                "【推荐科室】\n优先建议：消化内科",
                planWithDurationAndMedication(),
                "quick",
                2,
                true
        );

        assertThat(evaluation.mayFinish()).isTrue();
        assertThat(evaluation.terminalReason()).isEqualTo("PATIENT_UNKNOWN");
        assertThat(evaluation.unresolvedSlotNames()).doesNotContain("medication_detail");
    }

    @Test
    void criticalAskedPatientUnknownDoesNotLoop() {
        PreConsultationRequest request = requestWithHistory(
                "这个我不清楚。",
                "症状已经持续多久了？请说明大概从什么时候开始。"
        );

        CriticalSlotCompletionGate.Evaluation evaluation = gate.evaluate(
                request,
                request.question(),
                "我理解您暂时不清楚。",
                planDurationOnly(),
                "quick",
                2,
                false
        );

        assertThat(evaluation.mayFinish()).isTrue();
        assertThat(evaluation.terminalReason()).isEqualTo("PATIENT_UNKNOWN");
        assertThat(evaluation.unresolvedQuestions()).isEmpty();
    }

    @Test
    void criticalAskedPatientRefusedDoesNotLoop() {
        PreConsultationRequest request = requestWithHistory(
                "这个不方便回答。",
                "症状已经持续多久了？请说明大概从什么时候开始。"
        );

        CriticalSlotCompletionGate.Evaluation evaluation = gate.evaluate(
                request,
                request.question(),
                "我理解。",
                planDurationOnly(),
                "quick",
                2,
                false
        );

        assertThat(evaluation.mayFinish()).isTrue();
        assertThat(evaluation.terminalReason()).isEqualTo("PATIENT_REFUSED");
    }

    @Test
    void noMoreInformationDoesNotCloseNeverAskedCriticalSlot() {
        PreConsultationRequest request = request("这个我暂时没有更多补充。");

        CriticalSlotCompletionGate.Evaluation evaluation = gate.evaluate(
                request,
                request.question(),
                "请补充病情。",
                planDurationOnly(),
                "quick",
                2,
                false
        );

        assertThat(evaluation.mayFinish()).isFalse();
        assertThat(evaluation.terminalReason()).isEqualTo("CRITICAL_SLOT_UNRESOLVED");
        assertThat(evaluation.unresolvedQuestions()).contains("症状已经持续多久了？请说明大概从什么时候开始。");
    }

    @Test
    void noMoreInformationClosesAskedUnobtainableCriticalSlots() {
        PreConsultationRequest request = requestWithHistory(
                "这个我暂时没有更多补充。",
                "症状已经持续多久了？请说明大概从什么时候开始。"
        );

        CriticalSlotCompletionGate.Evaluation evaluation = gate.evaluate(
                request,
                request.question(),
                "好的。",
                planDurationOnly(),
                "quick",
                2,
                false
        );

        assertThat(evaluation.mayFinish()).isTrue();
        assertThat(evaluation.terminalReason()).isEqualTo("PATIENT_UNKNOWN");
    }

    @Test
    void resolvedCriticalSlotsFinishBeforeMaxRoundReason() {
        PreConsultationRequest request = request("咳嗽持续1周，中等程度，没有用药。");

        CriticalSlotCompletionGate.Evaluation evaluation = gate.evaluate(
                request,
                request.question(),
                "【推荐科室】呼吸内科",
                planWithDurationAndMedication(),
                "quick",
                3,
                true
        );

        assertThat(evaluation.mayFinish()).isTrue();
        assertThat(evaluation.terminalReason()).isEqualTo("CRITICAL_RESOLVED");
    }

    @Test
    void safetyTriggerAllowsCompletionWithoutCriticalSlotBlocking() {
        PreConsultationRequest request = request("呼吸困难明显。");

        CriticalSlotCompletionGate.Evaluation evaluation = gate.evaluate(
                request,
                request.question(),
                "请立即拨打120。",
                emergencyPlan(),
                "quick",
                1,
                false
        );

        assertThat(evaluation.mayFinish()).isTrue();
        assertThat(evaluation.terminalReason()).isEqualTo("SAFETY_TERMINATION");
    }

    @Test
    void inactiveConditionalSlotDoesNotEnterDenominator() {
        PreConsultationRequest request = request("咳嗽持续1周。");

        CriticalSlotCompletionGate.Evaluation evaluation = gate.evaluate(
                request,
                request.question(),
                "【推荐科室】呼吸内科",
                planDurationOnly(),
                "quick",
                2,
                true
        );

        assertThat(evaluation.slots()).extracting(CriticalSlotCompletionGate.SlotStatus::name)
                .containsExactly("duration");
        assertThat(evaluation.terminalReason()).isEqualTo("CRITICAL_RESOLVED");
    }

    @Test
    void unresolvedCriticalSlotDoesNotTerminateAtMaxRoundsWithoutLegalTerminalReason() {
        PreConsultationRequest request = request("腹痛持续1天，中等疼痛。");

        CriticalSlotCompletionGate.Evaluation evaluation = gate.evaluate(
                request,
                request.question(),
                "【推荐科室】\n优先建议：消化内科",
                planWithDurationAndMedication(),
                "quick",
                3,
                true
        );

        assertThat(evaluation.mayFinish()).isFalse();
        assertThat(evaluation.terminalReason()).isEqualTo("CRITICAL_SLOT_UNRESOLVED");
        assertThat(evaluation.unresolvedSlotNames()).contains("medication_detail");
        assertThat(evaluation.unresolvedQuestions()).isNotEmpty();
    }

    private PreConsultationRequest request(String question) {
        return new PreConsultationRequest("quick", "test-session", question, 2, List.of(), null);
    }

    private PreConsultationRequest requestWithHistory(String question, String assistantQuestion) {
        return new PreConsultationRequest("quick", "test-session", question, 2,
                List.of(new PreConsultationRequest.Message("assistant", assistantQuestion)), null);
    }

    private QuestionPlan planDurationOnly() {
        return new QuestionPlan(
                "medium",
                List.of("呼吸内科"),
                "尽快就医",
                List.of("咳嗽开始时间、持续多久"),
                List.of(),
                List.of(),
                List.of("起病时间", "持续时间"),
                List.of("主诉", "现病史"),
                List.of("咳嗽问诊模板"),
                List.of("symptom_inquiry"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "No patient-positive red flag evidence",
                "Respiratory department primary"
        );
    }

    private QuestionPlan emergencyPlan() {
        return new QuestionPlan(
                "high",
                List.of("急诊科", "呼吸内科"),
                "立即拨打120",
                List.of("呼吸困难程度和是否能完整说话"),
                List.of("静息呼吸困难或不能完整说话"),
                List.of(),
                List.of("起病时间", "持续时间", "用药史"),
                List.of("主诉", "现病史", "用药史"),
                List.of("呼吸困难红旗风险规则"),
                List.of("red_flag"),
                List.of("静息呼吸困难或不能完整说话"),
                List.of("静息呼吸困难或不能完整说话"),
                List.of(),
                List.of("patient-positive red flag evidence"),
                "Activated emergency red flag",
                "Emergency department primary"
        );
    }

    private QuestionPlan planWithDurationAndMedication() {
        return new QuestionPlan(
                "medium",
                List.of("消化内科", "普外科"),
                "尽快就医",
                List.of("腹痛开始时间、部位、性质和程度"),
                List.of(),
                List.of(),
                List.of("起病时间", "持续时间", "用药史"),
                List.of("主诉", "现病史", "用药史"),
                List.of("腹痛问诊模板"),
                List.of("symptom_inquiry"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "No patient-positive red flag evidence",
                "Digestive department primary"
        );
    }
}
