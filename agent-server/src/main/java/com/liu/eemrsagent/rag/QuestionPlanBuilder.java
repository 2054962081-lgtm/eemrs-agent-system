package com.liu.eemrsagent.rag;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class QuestionPlanBuilder {

    private static final List<String> DOC_TYPE_PRIORITY = List.of(
            "red_flag",
            "symptom_inquiry",
            "special_population",
            "department_triage",
            "medical_record_template"
    );
    private static final List<String> QUESTION_PRIORITY = List.of(
            "symptom_inquiry",
            "special_population",
            "red_flag",
            "department_triage",
            "medical_record_template"
    );
    private static final Pattern SPLIT_PATTERN = Pattern.compile("[,，;；、/\\s]+");

    private final RagProperties properties;

    public QuestionPlanBuilder(RagProperties properties) {
        this.properties = properties;
    }

    public QuestionPlan build(List<RagChunk> chunks, String mode) {
        return build(chunks, mode, "");
    }

    public QuestionPlan build(List<RagChunk> chunks, String mode, String userInput) {
        if (!properties.getQuestionPlan().isEnabled() || chunks == null || chunks.isEmpty()) {
            return QuestionPlan.empty();
        }
        int maxQuestions = "deep".equals(mode)
                ? properties.getQuestionPlan().getMaxKeyQuestionsDeep()
                : properties.getQuestionPlan().getMaxKeyQuestionsQuick();

        List<RagChunk> ordered = chunks.stream()
                .filter(chunk -> chunk != null)
                .sorted(Comparator
                        .comparingInt((RagChunk chunk) -> DOC_TYPE_PRIORITY.indexOf(chunk.docType()) < 0 ? 99 : DOC_TYPE_PRIORITY.indexOf(chunk.docType()))
                        .thenComparing((RagChunk chunk) -> chunk.score() == null ? 0.0 : -chunk.score()))
                .toList();

        LinkedHashSet<String> keyQuestions = new LinkedHashSet<>();
        LinkedHashSet<String> redFlags = new LinkedHashSet<>();
        LinkedHashSet<String> forbiddenActions = new LinkedHashSet<>();
        LinkedHashSet<String> expectedResponsePoints = new LinkedHashSet<>();
        LinkedHashSet<String> doctorRecordFields = new LinkedHashSet<>();
        LinkedHashSet<String> departments = new LinkedHashSet<>();
        LinkedHashSet<String> titles = new LinkedHashSet<>();
        LinkedHashSet<String> docTypes = new LinkedHashSet<>();
        String urgency = "";

        addAll(keyQuestions, scenarioQuestions(userInput), maxQuestions);

        List<RagChunk> questionOrdered = ordered.stream()
                .sorted(Comparator
                        .comparingInt((RagChunk chunk) -> QUESTION_PRIORITY.indexOf(chunk.docType()) < 0 ? 99 : QUESTION_PRIORITY.indexOf(chunk.docType()))
                        .thenComparing((RagChunk chunk) -> chunk.score() == null ? 0.0 : -chunk.score()))
                .toList();

        for (RagChunk chunk : questionOrdered) {
            addAll(keyQuestions, chunk.mustAsk(), maxQuestions);
        }

        for (RagChunk chunk : ordered) {
            addAll(redFlags, chunk.redFlags(), 12);
            addAll(forbiddenActions, chunk.forbiddenActions(), 12);
            addAll(expectedResponsePoints, chunk.expectedResponsePoints(), 12);
            addAll(doctorRecordFields, chunk.doctorRecordFields(), 12);
            splitAndAdd(departments, chunk.relatedDepartments(), 8);
            addIfPresent(titles, chunk.title(), 8);
            addIfPresent(docTypes, chunk.docType(), 8);
            if (urgency.isBlank() && chunk.urgencyLevel() != null && !chunk.urgencyLevel().isBlank()) {
                urgency = chunk.urgencyLevel().trim();
            }
        }

        String riskLevel = inferRiskLevel(urgency, redFlags);
        return new QuestionPlan(
                riskLevel,
                List.copyOf(departments),
                urgency,
                List.copyOf(keyQuestions),
                List.copyOf(redFlags),
                List.copyOf(forbiddenActions),
                List.copyOf(expectedResponsePoints),
                List.copyOf(doctorRecordFields),
                List.copyOf(titles),
                List.copyOf(docTypes)
        );
    }

    private List<String> scenarioQuestions(String input) {
        String text = input == null ? "" : input;
        List<String> questions = new ArrayList<>();
        if (containsAny(text, "失眠", "睡不着")) {
            questions.addAll(List.of("是否有自杀意念", "是否有自伤计划", "现在是否独处", "每天睡眠时长和持续多久", "是否有抑郁症史、物质使用或可联系支持者"));
        }
        if (containsAny(text, "不想活", "自杀", "撑不下去", "绝望", "活着没意思")) {
            questions.addAll(List.of("是否有自杀或自伤计划", "身边是否有工具或药物", "现在是否独处", "是否已经伤害自己", "当前位置在哪里，能否联系家属朋友陪伴"));
        }
        if (containsAny(text, "一大把", "安眠药", "大量药", "误服", "吃了几片")) {
            questions.addAll(List.of("药物名称是什么", "大概服用数量是多少", "服用时间是什么时候", "目前意识状态和呼吸情况如何", "现在是否独处，能否立即联系他人或120"));
        }
        if (containsAny(text, "摔", "跌倒", "髋部", "站不起来")) {
            questions.addAll(List.of("跌倒机制和受伤时间", "是否能负重或站立行走", "疼痛程度如何", "肢体是否变形或活动受限", "是否有头部受伤或正在使用抗凝药"));
        }
        if (containsAny(text, "糖尿病", "血糖")) {
            if (containsAny(text, "口渴", "多尿", "呕吐", "很高")) {
                questions.addAll(List.of("血糖具体数值是多少", "是否测过尿酮或血酮", "呕吐次数和能否进水", "意识状态和呼吸是否深快", "是否停药或有感染诱因"));
            } else {
                questions.addAll(List.of("近期血糖具体数值", "是否出汗心慌手抖或意识改变", "是否按时进食和用药", "是否使用胰岛素或降糖药", "症状持续多久"));
            }
        }
        if (containsAny(text, "眼", "视力", "看东西", "彩圈", "虹视")) {
            questions.addAll(List.of("视力下降开始时间", "是单眼还是双眼", "是否眼痛和头痛恶心", "是否看到灯光彩圈或虹视", "既往是否有青光眼或眼压高"));
        }
        if (containsAny(text, "血压", "190", "180/")) {
            questions.addAll(List.of("血压具体数值和复测结果", "头痛程度和持续时间", "是否胸痛气短", "是否视物模糊或肢体无力", "是否规律服用降压药"));
        }
        if (containsAny(text, "睾丸", "阴囊")) {
            questions.addAll(List.of("疼痛开始时间", "是否突发剧痛", "睾丸位置是否变高或肿胀", "是否恶心呕吐", "是否有外伤或发热尿痛"));
        }
        if (containsAny(text, "意识混乱", "说胡话", "不认识人", "没精神")) {
            questions.addAll(List.of("意识改变开始时间", "是否发热或感染表现", "是否头痛呕吐或头部外伤", "是否肢体无力或说话不清", "近期用药和血糖血压情况"));
        }
        if (containsAny(text, "抗凝", "华法林", "利伐沙班", "头外伤", "撞到头")) {
            questions.addAll(List.of("头部是否受伤及受伤时间", "是否短暂昏迷或意识改变", "是否头痛加重或反复呕吐", "正在使用哪种抗凝药", "是否有出血或神经系统症状"));
        }
        if (containsAny(text, "喘", "哮喘", "说话费劲", "呼吸困难")) {
            questions.addAll(List.of("呼吸困难程度和是否能完整说话", "是否口唇发紫或胸凹", "血氧饱和度是多少", "雾化或吸入药后是否缓解", "是否发热胸痛或既往哮喘"));
        }
        if (containsAny(text, "怀孕", "孕", "胎动", "阴道出血", "流液", "产后", "恶露")) {
            questions.addAll(List.of("孕周或产后天数", "是否阴道出血或流液", "腹痛程度和持续时间", "胎动是否减少", "血压水肿和头痛眼花情况"));
        }
        if (containsAny(text, "宝宝", "孩子", "女儿", "儿子", "婴儿", "高热", "抽搐")) {
            questions.addAll(List.of("孩子年龄和最高体温", "精神状态是否差", "呼吸是否费力", "饮水吃奶和尿量如何", "是否抽搐或皮疹"));
        }
        return questions;
    }

    private boolean containsAny(String text, String... keywords) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String inferRiskLevel(String urgency, Set<String> redFlags) {
        String text = (urgency == null ? "" : urgency) + " " + String.join(" ", redFlags);
        if (text.contains("120") || text.contains("emergency") || text.contains("急诊") || text.contains("立即")) {
            return "high";
        }
        if (text.contains("urgent") || text.contains("尽快") || text.contains("高危")) {
            return "medium";
        }
        return redFlags.isEmpty() ? "normal" : "medium";
    }

    private void addAll(LinkedHashSet<String> target, List<String> values, int limit) {
        if (values == null) {
            return;
        }
        for (String value : values) {
            addIfPresent(target, value, limit);
        }
    }

    private void splitAndAdd(LinkedHashSet<String> target, String value, int limit) {
        if (value == null || value.isBlank()) {
            return;
        }
        for (String item : SPLIT_PATTERN.split(value)) {
            addIfPresent(target, item, limit);
        }
    }

    private void addIfPresent(LinkedHashSet<String> target, String value, int limit) {
        if (target.size() >= limit || value == null) {
            return;
        }
        String normalized = value.trim();
        if (!normalized.isBlank()) {
            target.add(normalized);
        }
    }
}
