package com.liu.eemrsagent.rag;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Component
public class MustAskCoveragePostProcessor {

    private final RagProperties properties;

    public MustAskCoveragePostProcessor(RagProperties properties) {
        this.properties = properties;
    }

    public PostProcessResult process(String reply, QuestionPlan plan) {
        return process(reply, plan, List.of());
    }

    public PostProcessResult process(String reply, QuestionPlan plan, List<String> runtimeRequiredQuestions) {
        String safeReply = reply == null ? "" : reply;
        List<String> planQuestions = plan == null ? List.of() : plan.keyQuestions();
        List<String> runtimeQuestions = runtimeRequiredQuestions == null ? List.of() : runtimeRequiredQuestions;
        if (!properties.getPostProcess().isEnabled()) {
            double coverage = coverage(safeReply, planQuestions);
            return new PostProcessResult(safeReply, coverage, coverage, 0, List.of(), false);
        }

        List<String> missing = missingQuestions(safeReply, planQuestions);
        double before = ratio(planQuestions.size() - missing.size(), planQuestions.size());
        List<String> missingRuntime = missingQuestions(safeReply, runtimeQuestions);
        if ((before >= properties.getQuestionPlan().getMustAskTargetCoverage() || missing.isEmpty()) && missingRuntime.isEmpty()) {
            return new PostProcessResult(safeReply, before, before, 0, List.of(), true);
        }

        int max = Math.max(0, properties.getPostProcess().getMaxAddedQuestions());
        List<String> added = combineRuntimeFirst(missingRuntime, missing, max);
        String finalReply = appendQuestions(safeReply, plan, added);
        double after = coverage(finalReply, planQuestions);
        return new PostProcessResult(finalReply, before, after, added.size(), added, true);
    }

    private List<String> combineRuntimeFirst(List<String> runtimeQuestions, List<String> planQuestions, int max) {
        if (max <= 0) {
            return List.of();
        }
        LinkedHashSet<String> combined = new LinkedHashSet<>();
        combined.addAll(runtimeQuestions == null ? List.of() : runtimeQuestions);
        combined.addAll(planQuestions == null ? List.of() : planQuestions);
        return combined.stream().limit(max).toList();
    }

    private String appendQuestions(String reply, QuestionPlan plan, List<String> questions) {
        if (questions.isEmpty()) {
            return reply;
        }
        String heading = isEmergency(plan)
                ? "在尽快就医或等待急救时，可以同步准备/告知医生以下信息："
                : "为完善预问诊，还建议补充确认以下关键信息：";
        StringBuilder builder = new StringBuilder(reply == null ? "" : reply.trim());
        if (!builder.isEmpty()) {
            builder.append("\n\n");
        }
        builder.append("【补充关键问题】\n").append(heading).append("\n");
        for (int index = 0; index < questions.size(); index++) {
            builder.append(index + 1).append(". ").append(questions.get(index)).append("\n");
        }
        return builder.toString().trim();
    }

    private boolean isEmergency(QuestionPlan plan) {
        if (plan == null) {
            return false;
        }
        String text = (plan.riskLevel() + " " + plan.urgencyLevel() + " " + String.join(" ", plan.redFlags())).toLowerCase();
        return text.contains("high") || text.contains("120") || text.contains("急诊") || text.contains("立即") || text.contains("危");
    }

    private List<String> missingQuestions(String reply, List<String> keyQuestions) {
        List<String> missing = new ArrayList<>();
        for (String question : keyQuestions) {
            if (!isCovered(reply, question)) {
                missing.add(question);
            }
        }
        return missing;
    }

    private double coverage(String reply, List<String> keyQuestions) {
        if (keyQuestions == null || keyQuestions.isEmpty()) {
            return 0.0;
        }
        int covered = 0;
        for (String question : keyQuestions) {
            if (isCovered(reply, question)) {
                covered++;
            }
        }
        return ratio(covered, keyQuestions.size());
    }

    private boolean isCovered(String reply, String question) {
        if (reply == null || question == null || question.isBlank()) {
            return false;
        }
        String normalizedReply = normalize(reply);
        String normalizedQuestion = normalize(question);
        if (normalizedQuestion.length() >= 4 && normalizedReply.contains(normalizedQuestion)) {
            return true;
        }
        for (String token : normalizedQuestion.split("[,，;；、/\\s？?：:。]+")) {
            if (token.length() >= 2 && normalizedReply.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase();
    }

    private double ratio(int hit, int total) {
        if (total <= 0) {
            return 0.0;
        }
        return Math.round((hit * 1.0 / total) * 10000.0) / 10000.0;
    }

    public record PostProcessResult(
            String reply,
            double coverageBefore,
            double coverageAfter,
            int addedQuestionCount,
            List<String> addedQuestions,
            boolean usedPostProcess
    ) {
    }
}
