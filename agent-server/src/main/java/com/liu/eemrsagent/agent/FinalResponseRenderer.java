package com.liu.eemrsagent.agent;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class FinalResponseRenderer {

    private static final Pattern SECTION_HEADING = Pattern.compile("^【([^】]+)】\\s*$", Pattern.MULTILINE);

    RenderResult render(String reply, FinalRoutingResolver.FinalRoutingDecision decision) {
        return render(reply, decision, false);
    }

    RenderResult render(String reply, FinalRoutingResolver.FinalRoutingDecision decision, boolean terminal) {
        return render(reply, decision, terminal, terminal);
    }

    RenderResult render(String reply, FinalRoutingResolver.FinalRoutingDecision decision,
                        PreConsultationService.CompletionDecision completionDecision) {
        return render(reply, decision, completionDecision.shouldStopConversation(), completionDecision.taskComplete());
    }

    private RenderResult render(String reply, FinalRoutingResolver.FinalRoutingDecision decision,
                                boolean shouldStopConversation, boolean taskComplete) {
        String safeReply = reply == null ? "" : reply.trim();
        if (shouldStopConversation) {
            safeReply = removeSections(safeReply, "还需要确认", "下一步需要了解", "为什么需要这些信息", "补充关键问题");
        }
        String beforeRecommendation = sectionBefore(safeReply, "推荐科室");
        String afterCurrentAdvice = removeSections(safeReply, "推荐科室", "就医建议", "当前就医建议");
        if (!beforeRecommendation.isBlank() && afterCurrentAdvice.startsWith(beforeRecommendation)) {
            afterCurrentAdvice = afterCurrentAdvice.substring(beforeRecommendation.length()).trim();
        }
        String routingBlock = structuredRoutingBlock(decision);
        String rendered = joinBlocks(beforeRecommendation, routingBlock, afterCurrentAdvice);
        boolean conditionalEmergency = containsConditionalEmergencyAdvice(rendered);
        String renderedDepartment = extractPrimaryDepartment(rendered);
        String renderedUrgency = extractCurrentUrgency(rendered);
        boolean consistent = normalize(renderedDepartment).contains(normalize(decision.recommendedDepartment()))
                && urgencyConsistent(decision.urgency(), renderedUrgency);
        return new RenderResult(
                rendered,
                renderedDepartment,
                renderedUrgency,
                conditionalEmergency,
                consistent
        );
    }

    private String structuredRoutingBlock(FinalRoutingResolver.FinalRoutingDecision decision) {
        String department = blankToDefault(decision.recommendedDepartment(), "线下门诊");
        return """
                【推荐科室】
                建议优先就诊%s。

                【当前就医建议】
                %s
                """.formatted(department, currentUrgencyText(decision.urgency())).trim();
    }

    private String currentUrgencyText(String urgency) {
        return switch (urgency == null ? "" : urgency) {
            case "emergency" -> "当前建议立即前往急诊或拨打120。";
            case "urgent" -> "建议尽快线下就医。";
            case "observe" -> "可先短期观察；如症状持续、加重或出现危险信号，请及时线下就医。";
            default -> "建议根据症状变化安排线下就医；如症状加重，请及时就诊。";
        };
    }

    private String sectionBefore(String reply, String heading) {
        SectionRange range = findSection(reply, heading);
        if (range == null) {
            return reply;
        }
        return reply.substring(0, range.start()).trim();
    }

    private String removeSections(String reply, String... headings) {
        String result = reply == null ? "" : reply;
        for (String heading : headings) {
            SectionRange range = findSection(result, heading);
            if (range != null) {
                result = (result.substring(0, range.start()) + "\n\n" + result.substring(range.end())).trim();
            }
        }
        return result.trim();
    }

    private SectionRange findSection(String text, String heading) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = SECTION_HEADING.matcher(text);
        while (matcher.find()) {
            String current = matcher.group(1).trim();
            if (!current.equals(heading)) {
                continue;
            }
            int start = matcher.start();
            int end = text.length();
            if (matcher.find()) {
                end = matcher.start();
            }
            return new SectionRange(start, end);
        }
        return null;
    }

    private String joinBlocks(String... blocks) {
        StringBuilder builder = new StringBuilder();
        for (String block : blocks) {
            if (block == null || block.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append("\n\n");
            }
            builder.append(block.trim());
        }
        return builder.toString().trim();
    }

    private String extractPrimaryDepartment(String reply) {
        String section = sectionText(reply, "推荐科室");
        if (section.isBlank()) {
            return "";
        }
        for (String line : section.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("建议优先就诊")) {
                return trimmed.replace("建议优先就诊", "").replace("。", "").trim();
            }
            if (trimmed.startsWith("优先建议")) {
                return trimmed.replace("优先建议：", "").replace("优先建议:", "").trim();
            }
        }
        return section.trim();
    }

    private String extractCurrentUrgency(String reply) {
        String section = sectionText(reply, "当前就医建议");
        if (section.isBlank()) {
            return "";
        }
        String normalized = normalize(section);
        if (normalized.contains("立即") || normalized.contains("120") || normalized.contains("急诊")) {
            return "emergency";
        }
        if (normalized.contains("尽快") || normalized.contains("及时") || normalized.contains("尽早")) {
            return "urgent";
        }
        if (normalized.contains("观察")) {
            return "observe";
        }
        return "normal";
    }

    private String sectionText(String reply, String heading) {
        SectionRange range = findSection(reply, heading);
        if (range == null) {
            return "";
        }
        String section = reply.substring(range.start(), range.end());
        return section.replaceFirst("^【" + Pattern.quote(heading) + "】\\s*", "").trim();
    }

    private boolean containsConditionalEmergencyAdvice(String reply) {
        if (reply == null || reply.isBlank()) {
            return false;
        }
        String[] sentences = reply.split("\\R|[。！？!?；;]");
        for (String sentence : sentences) {
            String normalized = normalize(sentence);
            if ((normalized.contains("急诊") || normalized.contains("120") || normalized.contains("立即就医"))
                    && (normalized.contains("如出现") || normalized.contains("如果出现") || normalized.contains("若出现")
                    || normalized.contains("一旦出现") || normalized.contains("出现以下") || normalized.contains("危险信号"))) {
                return true;
            }
        }
        return false;
    }

    private boolean urgencyConsistent(String structuredUrgency, String renderedUrgency) {
        String structured = structuredUrgency == null || structuredUrgency.isBlank() ? "normal" : structuredUrgency;
        String rendered = renderedUrgency == null || renderedUrgency.isBlank() ? "normal" : renderedUrgency;
        return structured.equals(rendered);
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private record SectionRange(int start, int end) {
    }

    record RenderResult(
            String reply,
            String renderedPrimaryDepartment,
            String renderedCurrentUrgency,
            boolean conditionalEmergencyAdvicePresent,
            boolean routingConsistency
    ) {
    }
}
