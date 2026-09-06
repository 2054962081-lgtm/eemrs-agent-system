package com.liu.eemrsagent.agent;

import com.liu.eemrsagent.rag.QuestionPlan;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

class CriticalSlotCompletionGate {

    static final String DURATION = "duration";
    static final String SEVERITY = "severity";
    static final String MEDICATION_DETAIL = "medication_detail";

    private static final Pattern DURATION_VALUE = Pattern.compile(
            "(\\d+\\s*(秒|分钟|小时|天|周|月|年)|半\\s*(小时|天|个月)|一[两二三四五六七八九十]?[个]?(小时|天|周|月|年)|[两二三四五六七八九十]+[个]?(小时|天|周|月|年)|昨天|前天|今天|昨晚|上午|下午|晚上|凌晨)"
    );

    Evaluation evaluate(PreConsultationRequest request, String input, String reply, QuestionPlan plan,
                        String mode, int round, boolean baseFinished) {
        String patientText = patientText(request, input);
        String assistantText = assistantText(request, reply);
        List<SlotStatus> statuses = new ArrayList<>();
        if (isDurationActive(plan)) {
            statuses.add(durationStatus(patientText, assistantText));
        }
        if (isSeverityActive(plan)) {
            statuses.add(severityStatus(patientText, assistantText));
        }
        if (isMedicationDetailActive(plan, patientText, assistantText)) {
            statuses.add(medicationDetailStatus(patientText, assistantText));
        }

        boolean safetyTermination = isSafetyTermination(plan);
        boolean patientUnknown = statuses.stream().anyMatch(status -> "UNKNOWN".equals(status.resolutionStatus()));
        boolean patientRefused = statuses.stream().anyMatch(status -> "REFUSED".equals(status.resolutionStatus()));
        boolean noMoreObtainable = !statuses.isEmpty() && statuses.stream()
                .filter(SlotStatus::active)
                .allMatch(status -> status.resolved()
                        || "UNKNOWN".equals(status.resolutionStatus())
                        || "REFUSED".equals(status.resolutionStatus()));
        boolean unresolvedBlocking = statuses.stream()
                .anyMatch(status -> status.active() && !status.resolved()
                        && !"UNKNOWN".equals(status.resolutionStatus())
                        && !"REFUSED".equals(status.resolutionStatus()));

        String terminalReason;
        boolean mayFinish;
        if (safetyTermination) {
            terminalReason = "SAFETY_TERMINATION";
            mayFinish = true;
        } else if (!statuses.isEmpty() && statuses.stream().allMatch(status -> !status.active() || status.resolved())) {
            terminalReason = "CRITICAL_RESOLVED";
            mayFinish = true;
        } else if (noMoreObtainable && patientRefused) {
            terminalReason = "PATIENT_REFUSED";
            mayFinish = true;
        } else if (noMoreObtainable && patientUnknown) {
            terminalReason = "PATIENT_UNKNOWN";
            mayFinish = true;
        } else if (noMoreObtainable) {
            terminalReason = "NO_MORE_OBTAINABLE_INFORMATION";
            mayFinish = true;
        } else if (unresolvedBlocking) {
            terminalReason = "CRITICAL_SLOT_UNRESOLVED";
            mayFinish = false;
        } else if (baseFinished) {
            terminalReason = "ENOUGH_INFORMATION";
            mayFinish = true;
        } else {
            terminalReason = "NEED_MORE_INFORMATION";
            mayFinish = false;
        }

        List<String> questions = mayFinish ? List.of() : statuses.stream()
                .filter(status -> status.active() && !status.resolved())
                .filter(status -> !"UNKNOWN".equals(status.resolutionStatus()))
                .filter(status -> !"REFUSED".equals(status.resolutionStatus()))
                .map(this::followUpQuestion)
                .filter(question -> !question.isBlank())
                .distinct()
                .toList();
        return new Evaluation(statuses, questions, unresolvedBlocking, terminalReason, mayFinish);
    }

    private SlotStatus durationStatus(String patientText, String assistantText) {
        boolean asked = containsAny(assistantText, "多久", "持续", "什么时候开始", "开始时间", "起病时间");
        boolean resolved = DURATION_VALUE.matcher(patientText).find();
        boolean refused = asked && containsRefusal(patientText);
        boolean unknown = asked && !resolved && (containsUnknown(patientText) || containsNoMoreInformation(patientText));
        String status = resolved ? "RESOLVED" : refused ? "REFUSED" : unknown ? "UNKNOWN" : "ACTIVE_UNRESOLVED";
        return new SlotStatus(DURATION, true, asked, resolved || refused || unknown, resolved,
                status, resolved ? "duration value present" : status);
    }

    private SlotStatus medicationDetailStatus(String patientText, String assistantText) {
        boolean asked = askedMedicationDetail(assistantText);
        boolean refused = asked && containsRefusal(patientText);
        boolean unknownMedicationDetail = containsAny(patientText, "不知道药名", "不清楚药名", "记不住药名", "说不清药名");
        boolean unknown = unknownMedicationDetail
                || (asked && (containsUnknown(patientText) || containsNoMoreInformation(patientText)));
        boolean noMedication = containsAny(patientText, "没有吃药", "没吃药", "未服药", "没有用药", "没用药", "没吃过药");
        boolean disclosed = noMedication || unknown || refused || containsMedicationMention(patientText);
        boolean resolved = noMedication || (!unknown && !refused && medicationDetailResolved(patientText));
        String status = resolved ? "RESOLVED" : refused ? "REFUSED" : unknown ? "UNKNOWN" : "ACTIVE_UNRESOLVED";
        String evidence = resolved ? "medication detail or no-medication statement present" : status;
        return new SlotStatus(MEDICATION_DETAIL, true, asked, disclosed, resolved, status, evidence);
    }

    private SlotStatus severityStatus(String patientText, String assistantText) {
        boolean asked = containsAny(assistantText, "严重", "程度", "轻重", "多重", "疼痛评分", "几分", "加重");
        boolean resolved = containsAny(patientText,
                "轻度", "中度", "中等", "较重", "严重", "剧烈", "轻微", "明显", "不重", "很重", "难以忍受",
                "1分", "2分", "3分", "4分", "5分", "6分", "7分", "8分", "9分", "10分");
        boolean refused = asked && containsRefusal(patientText);
        boolean unknown = asked && !resolved && (containsUnknown(patientText) || containsNoMoreInformation(patientText));
        String status = resolved ? "RESOLVED" : refused ? "REFUSED" : unknown ? "UNKNOWN" : "ACTIVE_UNRESOLVED";
        return new SlotStatus(SEVERITY, true, asked, resolved || refused || unknown, resolved,
                status, resolved ? "severity value present" : status);
    }

    private boolean isDurationActive(QuestionPlan plan) {
        String text = planText(plan);
        return text.isBlank() || containsAny(text, "持续", "多久", "开始时间", "起病", "时间");
    }

    private boolean isMedicationDetailActive(QuestionPlan plan, String patientText, String assistantText) {
        String text = planText(plan);
        return containsAny(text, "用药", "服药", "药物", "药史")
                || containsMedicationMention(patientText)
                || containsAny(assistantText, "用药史", "药物", "服用");
    }

    private boolean isSeverityActive(QuestionPlan plan) {
        String text = planText(plan);
        return containsAny(text, "severity", "严重", "程度", "轻重", "性质和程度", "加重");
    }

    private String followUpQuestion(SlotStatus status) {
        if (DURATION.equals(status.name())) {
            return "症状已经持续多久了？请说明大概从什么时候开始。";
        }
        if (SEVERITY.equals(status.name())) {
            return "目前症状严重程度如何？例如轻度、中等、较重或是否正在加重。";
        }
        if (MEDICATION_DETAIL.equals(status.name())) {
            return "近期是否使用过药物？如果有，请说明具体药名、用法用量和使用时间；如果不知道药名，也请直接说明。";
        }
        return "";
    }

    private boolean askedMedicationDetail(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = normalize(text);
        boolean medicationContext = containsAny(normalized, "药", "服用", "用过", "使用", "吃了");
        boolean detailIntent = containsAny(normalized, "具体", "药名", "什么药", "哪些药", "用法", "用量", "剂量", "频次", "一次", "一天几次", "使用时间");
        return medicationContext && detailIntent;
    }

    private boolean medicationDetailResolved(String text) {
        String normalized = normalize(text);
        if (!containsMedicationMention(normalized)) {
            return false;
        }
        return containsAny(normalized,
                "布洛芬", "对乙酰氨基酚", "阿司匹林", "华法林", "奥美拉唑", "蒙脱石散", "抗生素",
                "退热止痛药", "止痛药", "胃药", "非处方", "药名", "说明书", "一次", "一天", "每日",
                "剂量", "用量", "毫克", "mg", "片", "粒", "袋", "毫升", "ml");
    }

    private boolean containsMedicationMention(String text) {
        return containsAny(normalize(text), "药", "服用", "服药", "用药", "吃了", "使用过", "布洛芬", "阿司匹林", "华法林");
    }

    private String patientText(PreConsultationRequest request, String input) {
        StringBuilder builder = new StringBuilder(input == null ? "" : input);
        if (request != null) {
            for (PreConsultationRequest.Message message : request.safeHistory()) {
                if (message != null && "user".equalsIgnoreCase(message.role()) && message.content() != null) {
                    builder.append('\n').append(message.content());
                }
            }
        }
        return normalize(builder.toString());
    }

    private String assistantText(PreConsultationRequest request, String reply) {
        StringBuilder builder = new StringBuilder(reply == null ? "" : reply);
        if (request != null) {
            for (PreConsultationRequest.Message message : request.safeHistory()) {
                if (message != null && "assistant".equalsIgnoreCase(message.role()) && message.content() != null) {
                    builder.append('\n').append(message.content());
                }
            }
        }
        return normalize(builder.toString());
    }

    private String planText(QuestionPlan plan) {
        if (plan == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        parts.addAll(nullToEmpty(plan.keyQuestions()));
        parts.addAll(nullToEmpty(plan.expectedResponsePoints()));
        parts.addAll(nullToEmpty(plan.doctorRecordFields()));
        return normalize(String.join(" ", parts));
    }

    private boolean isSafetyTermination(QuestionPlan plan) {
        if (plan == null) {
            return false;
        }
        String urgency = normalize(plan.urgencyLevel());
        boolean emergencyUrgency = containsAny(urgency, "120", "急诊", "立即", "emergency");
        boolean activeRedFlag = plan.activatedRedFlags() != null && !plan.activatedRedFlags().isEmpty();
        return emergencyUrgency && activeRedFlag;
    }

    private boolean containsRefusal(String text) {
        return containsAny(text, "拒绝", "不想说", "不方便说", "不愿意说", "不想回答", "不方便回答");
    }

    private boolean containsUnknown(String text) {
        return containsAny(text, "不知道", "不清楚", "记不清", "记不住", "说不清", "不确定");
    }

    private boolean containsNoMoreInformation(String text) {
        return containsAny(text, "没有更多补充", "没有更多信息", "暂时没有更多", "没什么补充", "无更多补充");
    }

    private List<String> nullToEmpty(List<String> values) {
        return values == null ? List.of() : values;
    }

    private boolean containsAny(String text, String... needles) {
        String safeText = text == null ? "" : text;
        for (String needle : needles) {
            if (safeText.contains(normalize(needle))) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    record Evaluation(
            List<SlotStatus> slots,
            List<String> unresolvedQuestions,
            boolean hasUnresolvedBlockingSlot,
            String terminalReason,
            boolean mayFinish
    ) {
        List<String> unresolvedSlotNames() {
            return slots.stream()
                    .filter(status -> status.active() && !status.resolved()
                            && !"UNKNOWN".equals(status.resolutionStatus())
                            && !"REFUSED".equals(status.resolutionStatus()))
                    .map(SlotStatus::name)
                    .toList();
        }

        long activeSafetyCount() {
            return "SAFETY_TERMINATION".equals(terminalReason) ? 1 : 0;
        }

        long criticalSlotTotal() {
            return slots.stream().filter(SlotStatus::active).count();
        }

        long criticalSlotResolved() {
            return slots.stream()
                    .filter(status -> status.active() && "RESOLVED".equals(status.resolutionStatus()))
                    .count();
        }

        long criticalSlotUnknown() {
            return slots.stream()
                    .filter(status -> status.active() && "UNKNOWN".equals(status.resolutionStatus()))
                    .count();
        }

        long criticalSlotRefused() {
            return slots.stream()
                    .filter(status -> status.active() && "REFUSED".equals(status.resolutionStatus()))
                    .count();
        }

        long criticalSlotUnasked() {
            return slots.stream()
                    .filter(status -> status.active() && !status.asked())
                    .count();
        }

        long criticalSlotUnresolved() {
            return unresolvedSlotNames().size();
        }
    }

    record SlotStatus(
            String name,
            boolean active,
            boolean asked,
            boolean disclosed,
            boolean resolved,
            String resolutionStatus,
            String evidence
    ) {
    }
}
