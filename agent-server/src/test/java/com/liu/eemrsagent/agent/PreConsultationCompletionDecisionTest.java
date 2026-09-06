package com.liu.eemrsagent.agent;

import com.liu.eemrsagent.rag.QuestionPlan;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PreConsultationCompletionDecisionTest {

    private final PreConsultationService service = new PreConsultationService(
            null, null, null, null, null, null, null, null, null);
    private final CriticalSlotCompletionGate gate = new CriticalSlotCompletionGate();

    @Test
    void normalCompleteStopsAndCompletesTask() {
        PreConsultationRequest request = request("咳嗽持续1周，中等程度，没有用药。", 2, List.of());
        CriticalSlotCompletionGate.Evaluation slots = gate.evaluate(
                request, request.question(), "【推荐科室】呼吸内科", planWithDurationAndMedication(), "quick", 2, true);

        PreConsultationService.CompletionDecision decision =
                service.completionDecision(true, request, request.question(), "quick", 2, slots);

        assertThat(decision.shouldStopConversation()).isTrue();
        assertThat(decision.taskComplete()).isTrue();
        assertThat(decision.terminationReason()).isEqualTo("COMPLETED");
        assertThat(decision.remainingActionableCriticalSlots()).isEmpty();
    }

    @Test
    void prematureRecommendationWithRemainingMedicationDetailContinues() {
        PreConsultationRequest request = request("尿频尿急持续1天，中等程度。", 2, List.of());
        CriticalSlotCompletionGate.Evaluation slots = gate.evaluate(
                request,
                request.question(),
                "【推荐科室】泌尿外科\n【就医建议】具体药名和使用方式仍需明确。",
                planWithDurationAndMedication(),
                "quick",
                2,
                true);

        PreConsultationService.CompletionDecision decision =
                service.completionDecision(true, request, request.question(), "quick", 2, slots);

        assertThat(decision.shouldStopConversation()).isFalse();
        assertThat(decision.taskComplete()).isFalse();
        assertThat(decision.terminationReason()).isEqualTo("CRITICAL_SLOT_UNRESOLVED");
        assertThat(decision.remainingActionableCriticalSlots()).contains("medication_detail");
    }

    @Test
    void prematureRecommendationWithRemainingSeverityContinues() {
        PreConsultationRequest request = request("尿频尿急持续1天，没有用药。", 2, List.of());
        CriticalSlotCompletionGate.Evaluation slots = gate.evaluate(
                request,
                request.question(),
                "【推荐科室】泌尿外科\n【就医建议】建议尽快就诊。",
                planWithDurationSeverityAndMedication(),
                "quick",
                2,
                true);

        PreConsultationService.CompletionDecision decision =
                service.completionDecision(true, request, request.question(), "quick", 2, slots);

        assertThat(decision.shouldStopConversation()).isFalse();
        assertThat(decision.taskComplete()).isFalse();
        assertThat(decision.remainingActionableCriticalSlots()).contains("severity");
    }

    @Test
    void userNoMoreStopsWithoutCompletingTask() {
        PreConsultationRequest request = requestWithHistory(
                "这个我暂时没有更多补充。",
                2,
                "症状已经持续多久了？请说明大概从什么时候开始。");
        CriticalSlotCompletionGate.Evaluation slots = gate.evaluate(
                request, request.question(), "好的。", planDurationOnly(), "quick", 2, false);

        PreConsultationService.CompletionDecision decision =
                service.completionDecision(false, request, request.question(), "quick", 2, slots);

        assertThat(decision.shouldStopConversation()).isTrue();
        assertThat(decision.taskComplete()).isFalse();
        assertThat(decision.terminationReason()).isEqualTo("USER_NO_MORE_INFORMATION");
    }

    @Test
    void maxRoundsStopsButDoesNotCompleteWithRemainingCritical() {
        PreConsultationRequest request = request("腹痛持续1天，中等程度。", 3, List.of());
        CriticalSlotCompletionGate.Evaluation slots = gate.evaluate(
                request, request.question(), "【推荐科室】消化内科", planWithDurationAndMedication(), "quick", 3, true);

        PreConsultationService.CompletionDecision decision =
                service.completionDecision(true, request, request.question(), "quick", 3, slots);

        assertThat(decision.shouldStopConversation()).isTrue();
        assertThat(decision.taskComplete()).isFalse();
        assertThat(decision.terminationReason()).isEqualTo("MAX_ROUNDS_WITH_UNRESOLVED_CRITICAL");
    }

    @Test
    void safetyTerminalStopsAndKeepsTaskCompletionSeparateFromRemainingOrdinarySlot() {
        PreConsultationRequest request = request("呼吸困难明显。", 1, List.of());
        CriticalSlotCompletionGate.Evaluation slots = gate.evaluate(
                request, request.question(), "请立即拨打120。", emergencyPlanWithOrdinaryMedicationSlot(), "quick", 1, false);

        PreConsultationService.CompletionDecision decision =
                service.completionDecision(false, request, request.question(), "quick", 1, slots);

        assertThat(decision.shouldStopConversation()).isTrue();
        assertThat(decision.terminationReason()).isEqualTo("SAFETY_TERMINATION");
        assertThat(decision.taskComplete()).isFalse();
        assertThat(decision.remainingActionableCriticalSlots()).contains("duration", "medication_detail");
    }

    @Test
    void safetyTerminalCanCompleteTaskWhenNoActionableCriticalRemains() {
        PreConsultationRequest request = request("呼吸困难持续1天，中等程度，没有用药。", 1, List.of());
        CriticalSlotCompletionGate.Evaluation slots = gate.evaluate(
                request, request.question(), "请立即拨打120。", emergencyPlanWithOrdinaryMedicationSlot(), "quick", 1, false);

        PreConsultationService.CompletionDecision decision =
                service.completionDecision(false, request, request.question(), "quick", 1, slots);

        assertThat(decision.shouldStopConversation()).isTrue();
        assertThat(decision.terminationReason()).isEqualTo("SAFETY_TERMINATION");
        assertThat(decision.taskComplete()).isTrue();
        assertThat(decision.remainingActionableCriticalSlots()).isEmpty();
    }

    @Test
    void responseFinishedRemainsShouldStopCompatibilityField() {
        PreConsultationResponse response = PreConsultationResponse.ok(
                "quick", "reply", true, false, "USER_NO_MORE_INFORMATION",
                2, "心内科", "urgent", "model", "provider");

        assertThat(response.finished()).isTrue();
        assertThat(response.shouldStopConversation()).isTrue();
        assertThat(response.taskComplete()).isFalse();
        assertThat(response.terminationReason()).isEqualTo("USER_NO_MORE_INFORMATION");
    }

    private PreConsultationRequest request(String question, int round, List<PreConsultationRequest.Message> history) {
        return new PreConsultationRequest("quick", "test-session", question, round, history, null);
    }

    private PreConsultationRequest requestWithHistory(String question, int round, String assistantQuestion) {
        return request(question, round, List.of(new PreConsultationRequest.Message("assistant", assistantQuestion)));
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

    private QuestionPlan planWithDurationAndMedication() {
        return new QuestionPlan(
                "medium",
                List.of("消化内科"),
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

    private QuestionPlan planWithDurationSeverityAndMedication() {
        return planWithDurationAndMedication();
    }

    private QuestionPlan emergencyPlanWithOrdinaryMedicationSlot() {
        return new QuestionPlan(
                "high",
                List.of("急诊科"),
                "emergency",
                List.of("呼吸困难程度和是否能完整说话"),
                List.of("Dyspnea red flag"),
                List.of(),
                List.of("起病时间", "持续时间", "用药史"),
                List.of("主诉", "现病史", "用药史"),
                List.of("呼吸困难红旗风险规则"),
                List.of("red_flag"),
                List.of("静息呼吸困难或不能完整说话"),
                List.of("Dyspnea red flag"),
                List.of(),
                List.of("patient-positive red flag evidence"),
                "Activated emergency red flag",
                "Emergency department primary"
        );
    }
}
