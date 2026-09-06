package com.liu.eemrsagent.agent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

class PreConsultationRagQueryBuilder {
    private static final int MAX_FACTS = 8;
    private static final Pattern DURATION_PATTERN = Pattern.compile("\\d+\\s*(分钟|小时|天|周|月|年)|半天|一天|两天|三天|持续|多久|开始");
    private static final Pattern MEDICATION_PATTERN = Pattern.compile("药|服用|吃了|用了|止痛|退热|抗生素|布洛芬|对乙酰氨基酚|非处方");
    private static final Pattern SYMPTOM_PATTERN = Pattern.compile("痛|疼|发热|咳|吐|腹泻|便血|黑便|头晕|胸闷|呼吸|恶心|尿|皮疹|乏力");
    private static final Pattern PROGRESSION_PATTERN = Pattern.compile("加重|缓解|持续|反复|阵发|越来越|减轻");
    private static final Pattern SPECIAL_POPULATION_PATTERN = Pattern.compile("老人|老年|高龄|孕|儿童|婴幼儿|免疫|慢性病|ELDERLY|PREGNANT|CHILD");
    private static final Pattern SEVERITY_PATTERN = Pattern.compile("轻|中等|严重|剧烈|难忍|\\d+\\s*分|程度");

    RagQueryContext build(String currentInput, List<PreConsultationRequest.Message> history) {
        List<String> patientMessages = patientMessages(currentInput, history);
        String chiefComplaint = firstUseful(patientMessages, safe(currentInput));
        LinkedHashSet<String> facts = new LinkedHashSet<>();
        for (String message : patientMessages) {
            String normalized = normalize(message);
            if (normalized.isBlank() || normalized.equals(normalize(chiefComplaint))) {
                continue;
            }
            if (hasClinicalSignal(normalized) && !isLowInformation(normalized)) {
                facts.add(truncate(normalized, 80));
            }
            if (facts.size() >= MAX_FACTS) {
                break;
            }
        }
        if (!normalize(currentInput).equals(normalize(chiefComplaint))
                && hasClinicalSignal(currentInput)
                && !isLowInformation(currentInput)) {
            facts.add(truncate(normalize(currentInput), 80));
        }
        LinkedHashSet<String> resolvedSlots = new LinkedHashSet<>();
        for (String fact : facts) {
            resolvedSlots.addAll(inferSlots(fact));
        }
        String query = buildQuery(chiefComplaint, facts);
        boolean latestUsed = facts.stream().anyMatch(fact -> fact.equals(truncate(normalize(currentInput), 80)))
                || normalize(currentInput).equals(normalize(chiefComplaint));
        return new RagQueryContext(
                query,
                !normalize(chiefComplaint).isBlank(),
                resolvedSlots.size(),
                List.copyOf(resolvedSlots),
                latestUsed,
                !latestUsed,
                facts.isEmpty() ? "chief_complaint" : "chief_complaint_plus_patient_facts",
                query.length()
        );
    }

    private List<String> patientMessages(String currentInput, List<PreConsultationRequest.Message> history) {
        List<String> messages = new ArrayList<>();
        if (history != null) {
            for (PreConsultationRequest.Message message : history) {
                if (message == null || message.role() == null || message.content() == null) {
                    continue;
                }
                if ("user".equals(message.role().trim().toLowerCase(Locale.ROOT))) {
                    String content = normalize(message.content());
                    if (!content.isBlank()) {
                        messages.add(content);
                    }
                }
            }
        }
        String current = normalize(currentInput);
        if (!current.isBlank()) {
            messages.add(current);
        }
        return messages;
    }

    private String firstUseful(List<String> messages, String fallback) {
        for (String message : messages) {
            if (!isLowInformation(message) || hasClinicalSignal(message)) {
                return message;
            }
        }
        return normalize(fallback);
    }

    private String buildQuery(String chiefComplaint, LinkedHashSet<String> facts) {
        StringBuilder builder = new StringBuilder();
        builder.append("主诉/主要症状：").append(truncate(normalize(chiefComplaint), 120));
        if (!facts.isEmpty()) {
            builder.append("\n已知患者补充：").append(String.join("；", facts));
        }
        builder.append("\n任务：预问诊分诊、风险识别和科室推荐");
        return truncate(builder.toString(), 420);
    }

    private List<String> inferSlots(String text) {
        List<String> slots = new ArrayList<>();
        if (DURATION_PATTERN.matcher(text).find()) {
            slots.add("duration");
        }
        if (MEDICATION_PATTERN.matcher(text).find()) {
            slots.add("medication_detail");
        }
        if (SYMPTOM_PATTERN.matcher(text).find()) {
            slots.add("associated_symptoms");
        }
        if (PROGRESSION_PATTERN.matcher(text).find()) {
            slots.add("progression");
        }
        if (SPECIAL_POPULATION_PATTERN.matcher(text).find()) {
            slots.add("special_population");
        }
        if (SEVERITY_PATTERN.matcher(text).find()) {
            slots.add("severity");
        }
        return slots;
    }

    private boolean hasClinicalSignal(String text) {
        String value = normalize(text);
        return DURATION_PATTERN.matcher(value).find()
                || MEDICATION_PATTERN.matcher(value).find()
                || SYMPTOM_PATTERN.matcher(value).find()
                || PROGRESSION_PATTERN.matcher(value).find()
                || SPECIAL_POPULATION_PATTERN.matcher(value).find()
                || SEVERITY_PATTERN.matcher(value).find();
    }

    private boolean isLowInformation(String text) {
        String value = normalize(text);
        if (value.isBlank()) {
            return true;
        }
        if (value.length() <= 3 && !hasClinicalSignal(value)) {
            return true;
        }
        return value.matches("^(好的|嗯|是|不是|没有|无|不知道|不清楚|暂时没有|没有更多补充|先这样|谢谢)[。！!,.，]*$");
    }

    private String normalize(String value) {
        return safe(value).replaceAll("\\s+", " ").trim();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, maxLength);
    }

    record RagQueryContext(
            String query,
            boolean chiefComplaintPresent,
            int resolvedSlotCount,
            List<String> resolvedSlotNames,
            boolean latestMessageUsed,
            boolean latestMessageIgnored,
            String querySource,
            int queryLength
    ) {
    }
}
