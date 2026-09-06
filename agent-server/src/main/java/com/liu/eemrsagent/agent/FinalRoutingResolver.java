package com.liu.eemrsagent.agent;

import com.liu.eemrsagent.rag.QuestionPlan;

import java.util.List;
import java.util.Locale;

class FinalRoutingResolver {

    FinalRoutingDecision resolve(String reply, QuestionPlan plan) {
        String textCandidate = parseTextUrgencyCandidate(reply);
        boolean planEmergency = isPlanEmergency(plan);
        String plannedUrgency = mapPlanUrgency(plan == null ? "" : plan.urgencyLevel());
        if (!planEmergency && "emergency".equals(plannedUrgency)) {
            plannedUrgency = "urgent";
        }
        String urgency;
        String source;
        String reason;
        if (planEmergency) {
            urgency = "emergency";
            source = "QUESTION_PLAN";
            reason = "PLAN_EMERGENCY_OR_ACTIVE_RED_FLAG";
        } else if (!"normal".equals(plannedUrgency)) {
            urgency = plannedUrgency;
            source = "QUESTION_PLAN";
            reason = "emergency".equals(textCandidate) || containsConditionalEmergencyAdvice(reply)
                    ? "CONDITIONAL_EMERGENCY_ADVICE_IGNORED"
                    : "QUESTION_PLAN_PRECEDENCE";
        } else if ("emergency".equals(textCandidate)) {
            urgency = "normal";
            source = "QUESTION_PLAN";
            reason = "MODEL_EMERGENCY_WITHOUT_STRUCTURED_EVIDENCE_IGNORED";
        } else {
            urgency = textCandidate;
            source = textCandidate == null || textCandidate.isBlank() ? "QUESTION_PLAN" : "MODEL_TEXT";
            reason = "NO_STRUCTURED_URGENCY_AVAILABLE";
        }

        String department = primaryPlannedDepartment(plan);
        String departmentSource = department.isBlank() ? "MODEL_TEXT" : "QUESTION_PLAN";
        if (department.isBlank()) {
            department = extractRecommendedDepartment(reply);
        }
        return new FinalRoutingDecision(
                department,
                urgency,
                source,
                departmentSource,
                reason,
                textCandidate,
                plannedUrgency,
                plan == null ? "" : nullToBlank(plan.riskLevel()),
                plan == null || plan.activatedRedFlags() == null ? 0 : plan.activatedRedFlags().size()
        );
    }

    String parseTextUrgencyCandidate(String reply) {
        if (reply == null || reply.isBlank()) {
            return "normal";
        }
        String[] sentences = reply.split("\\R|[。！？!?；;]");
        boolean urgent = false;
        boolean observe = false;
        for (String sentence : sentences) {
            String normalized = normalize(sentence);
            if (normalized.isBlank()) {
                continue;
            }
            if (containsAny(normalized, "观察")) {
                observe = true;
            }
            if (containsAny(normalized, "尽快", "及时线下就医", "尽早", "近期就医")) {
                urgent = true;
            }
            if (isDirectEmergencySentence(normalized)) {
                return "emergency";
            }
        }
        if (urgent) {
            return "urgent";
        }
        return observe ? "observe" : "normal";
    }

    private boolean isPlanEmergency(QuestionPlan plan) {
        if (plan == null) {
            return false;
        }
        if (plan.activatedRedFlags() != null && !plan.activatedRedFlags().isEmpty()) {
            return true;
        }
        if (plan.redFlags() == null || plan.redFlags().isEmpty()) {
            return false;
        }
        String text = normalize(nullToBlank(plan.riskLevel()) + " " + nullToBlank(plan.urgencyLevel()));
        return containsAny(text, "emergency", "急诊", "立即", "120");
    }

    private String mapPlanUrgency(String urgencyLevel) {
        String text = normalize(urgencyLevel);
        if (text.isBlank()) {
            return "normal";
        }
        if (containsAny(text, "emergency", "急诊", "立即", "120")) {
            return "emergency";
        }
        if (containsAny(text, "urgent", "尽快", "及时", "尽早")) {
            return "urgent";
        }
        if (containsAny(text, "observe", "观察")) {
            return "observe";
        }
        return "normal";
    }

    private boolean isDirectEmergencySentence(String normalizedSentence) {
        if (!containsAny(normalizedSentence, "急诊", "立即就医", "马上就医", "拨打120", "120")) {
            return false;
        }
        if (containsAny(normalizedSentence, "如出现", "如果出现", "若出现", "一旦出现", "出现以下", "危险信号", "必要时", "需要时", "可考虑")) {
            return false;
        }
        return true;
    }

    private boolean containsConditionalEmergencyAdvice(String reply) {
        if (reply == null || reply.isBlank()) {
            return false;
        }
        String[] sentences = reply.split("\\R|[。！？!?；;]");
        for (String sentence : sentences) {
            String normalized = normalize(sentence);
            if (containsAny(normalized, "急诊", "立即就医", "马上就医", "拨打120", "120")
                    && containsAny(normalized, "如出现", "如果出现", "若出现", "一旦出现", "出现以下", "危险信号", "必要时", "需要时", "可考虑")) {
                return true;
            }
        }
        return false;
    }

    private String primaryPlannedDepartment(QuestionPlan plan) {
        if (plan == null || plan.recommendedDepartments() == null) {
            return "";
        }
        return plan.recommendedDepartments().stream()
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse("");
    }

    private String extractRecommendedDepartment(String reply) {
        if (reply == null || reply.isBlank() || !containsRecommendation(reply)) {
            return "";
        }
        String[] lines = reply.split("\\R");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("优先建议") || trimmed.contains("优先建议：")) {
                return trimmed.replace("优先建议：", "").replace("优先建议:", "").trim();
            }
        }
        return "";
    }

    private boolean containsRecommendation(String reply) {
        return reply != null && (reply.contains("【推荐科室】") || reply.contains("推荐科室") || reply.contains("优先建议"));
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

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    record FinalRoutingDecision(
            String recommendedDepartment,
            String urgency,
            String urgencySource,
            String departmentSource,
            String urgencyOverrideReason,
            String textUrgencyCandidate,
            String plannedUrgency,
            String plannedRisk,
            int activatedRedFlagCount
    ) {
    }
}
