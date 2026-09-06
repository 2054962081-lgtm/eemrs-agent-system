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
    private static final List<RulePattern> RED_FLAG_PATTERNS = List.of(
            new RulePattern("GI bleeding", List.of("消化道出血", "呕血", "黑便", "便血"), List.of("呕血", "黑便", "便血", "大量出血", "头晕心慌", "晕厥", "出冷汗"), List.of("无呕血", "无黑便", "无便血", "没有呕血", "没有黑便", "没有便血", "否认呕血", "否认黑便", "否认便血")),
            new RulePattern("Pregnancy red flag", List.of("孕", "孕产妇", "胎动", "阴道出血"), List.of("怀孕", "孕妇", "孕周", "孕期", "产后", "阴道出血", "胎动减少", "流液"), List.of("男性", "男", "未孕", "没有怀孕", "否认怀孕", "非孕")),
            new RulePattern("Chest pain red flag", List.of("胸痛", "胸闷", "冠心病"), List.of("胸痛", "胸闷", "大汗", "冒汗", "出汗", "放射痛", "气短", "恶心", "持续不缓解"), List.of("无胸痛", "没有胸痛", "否认胸痛", "无胸闷", "没有胸闷", "否认胸闷")),
            new RulePattern("Dyspnea red flag", List.of("呼吸困难", "气短", "喘", "咯血"), List.of("呼吸困难", "喘得厉害", "说话费劲", "口唇发紫", "血氧低", "咯血"), List.of("无呼吸困难", "没有呼吸困难", "否认呼吸困难", "无气短", "没有气短")),
            new RulePattern("Fever red flag", List.of("发热", "高热", "寒战", "免疫"), List.of("高热", "40度", "意识模糊", "寒战", "化疗后发热", "免疫低下"), List.of("无发热", "没有发热", "否认发热", "不发烧", "没发烧")),
            new RulePattern("Severe abdominal pain", List.of("腹痛", "腹肌紧张", "剧烈持续腹痛"), List.of("剧烈腹痛", "剧烈持续腹痛", "腹肌紧张", "板状腹", "休克", "晕厥"), List.of("中等疼痛", "轻微腹痛", "无腹肌紧张", "没有腹肌紧张", "否认明显伴随症状"))
    );

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
        LinkedHashSet<String> availableRedFlagRules = new LinkedHashSet<>();
        LinkedHashSet<String> forbiddenActions = new LinkedHashSet<>();
        LinkedHashSet<String> expectedResponsePoints = new LinkedHashSet<>();
        LinkedHashSet<String> doctorRecordFields = new LinkedHashSet<>();
        LinkedHashSet<String> departmentCandidates = new LinkedHashSet<>();
        LinkedHashSet<String> titles = new LinkedHashSet<>();
        LinkedHashSet<String> docTypes = new LinkedHashSet<>();
        String nonRuleUrgency = "";

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
            addAll(availableRedFlagRules, chunk.redFlags(), 12);
            addAll(forbiddenActions, chunk.forbiddenActions(), 12);
            addAll(expectedResponsePoints, chunk.expectedResponsePoints(), 12);
            addAll(doctorRecordFields, chunk.doctorRecordFields(), 12);
            splitAndAdd(departmentCandidates, chunk.relatedDepartments(), 12);
            addIfPresent(titles, chunk.title(), 8);
            addIfPresent(docTypes, chunk.docType(), 8);
            if (!"red_flag".equals(chunk.docType()) && nonRuleUrgency.isBlank() && chunk.urgencyLevel() != null && !chunk.urgencyLevel().isBlank()) {
                nonRuleUrgency = chunk.urgencyLevel().trim();
            }
        }

        RedFlagDecision redFlagDecision = decideRedFlags(userInput, chunks, availableRedFlagRules);
        String urgency = inferUrgency(nonRuleUrgency, redFlagDecision);
        String riskLevel = inferRiskLevel(urgency, redFlagDecision.activatedRedFlags());
        DepartmentDecision departmentDecision = decideDepartments(userInput, departmentCandidates, chunks, redFlagDecision);
        return new QuestionPlan(
                riskLevel,
                departmentDecision.departments(),
                urgency,
                List.copyOf(keyQuestions),
                redFlagDecision.activatedRedFlags(),
                List.copyOf(forbiddenActions),
                List.copyOf(expectedResponsePoints),
                List.copyOf(doctorRecordFields),
                List.copyOf(titles),
                List.copyOf(docTypes),
                List.copyOf(availableRedFlagRules),
                redFlagDecision.activatedRedFlags(),
                redFlagDecision.inactiveRedFlags(),
                redFlagDecision.activationEvidence(),
                redFlagDecision.reason(),
                departmentDecision.reason()
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

    private RedFlagDecision decideRedFlags(String userInput, List<RagChunk> chunks, Set<String> availableRules) {
        String patientText = userInput == null ? "" : userInput;
        String ruleText = new StringBuilder()
                .append(String.join(" ", availableRules))
                .append(" ")
                .append(chunks == null ? "" : chunks.stream()
                        .filter(chunk -> chunk != null)
                        .map(chunk -> String.join(" ", List.of(
                                value(chunk.title()),
                                value(chunk.chunkId()),
                                value(chunk.docId()),
                                value(chunk.relatedSymptoms()),
                                value(chunk.applicablePopulation()))))
                        .reduce("", (left, right) -> left + " " + right))
                .toString();
        LinkedHashSet<String> activated = new LinkedHashSet<>();
        LinkedHashSet<String> inactive = new LinkedHashSet<>();
        LinkedHashSet<String> evidence = new LinkedHashSet<>();
        for (RulePattern pattern : RED_FLAG_PATTERNS) {
            if (!pattern.matchesRule(ruleText)) {
                continue;
            }
            if (pattern.hasNegativeEvidence(patientText)) {
                inactive.add(pattern.name() + ": NEGATIVE_EVIDENCE");
                evidence.add(pattern.name() + " negative evidence in patient state");
                continue;
            }
            if (pattern.hasPositiveEvidence(patientText)) {
                activated.add(pattern.name());
                evidence.add(pattern.name() + " positive evidence in patient state");
            } else {
                inactive.add(pattern.name() + ": UNKNOWN");
            }
        }
        String reason = activated.isEmpty()
                ? "No patient-positive red flag evidence; available rules remain follow-up prompts."
                : "Risk escalated by patient-positive red flag evidence: " + String.join("; ", activated);
        return new RedFlagDecision(
                List.copyOf(activated),
                List.copyOf(inactive),
                List.copyOf(evidence),
                reason
        );
    }

    private String inferUrgency(String nonRuleUrgency, RedFlagDecision redFlagDecision) {
        if (!redFlagDecision.activatedRedFlags().isEmpty()) {
            return "emergency";
        }
        String urgency = nonRuleUrgency == null ? "" : nonRuleUrgency.trim();
        if (urgency.contains("立即") || urgency.contains("120") || urgency.contains("急诊")) {
            return "urgent";
        }
        return urgency;
    }

    private String inferRiskLevel(String urgency, List<String> activatedRedFlags) {
        if (!activatedRedFlags.isEmpty()) {
            return "high";
        }
        String text = urgency == null ? "" : urgency;
        if (text.contains("urgent") || text.contains("尽快") || text.contains("高危")) {
            return "medium";
        }
        return "normal";
    }

    private DepartmentDecision decideDepartments(
            String userInput,
            Set<String> departmentCandidates,
            List<RagChunk> chunks,
            RedFlagDecision redFlagDecision
    ) {
        String text = userInput == null ? "" : userInput;
        boolean abdominal = containsAny(text, "腹痛", "肚子痛", "肚痛", "腹胀", "上腹痛", "下腹痛");
        boolean urinary = containsAny(text, "尿频", "尿急", "尿痛", "血尿", "小便", "排尿", "腰痛");
        boolean pregnancy = hasPregnancyEvidence(text);
        boolean chest = containsAny(text, "胸痛", "胸闷", "大汗", "冒汗", "出汗", "放射痛");
        boolean chemo = containsAny(text, "化疗", "肿瘤", "免疫低下", "免疫抑制");
        boolean emergency = !redFlagDecision.activatedRedFlags().isEmpty();
        LinkedHashSet<String> departments = new LinkedHashSet<>();
        for (String department : departmentCandidates) {
            if (departments.size() >= 6) {
                break;
            }
            if (department == null || department.isBlank()) {
                continue;
            }
            String normalized = department.trim();
            if (emergency && normalized.equals("急诊科")) {
                departments.add(normalized);
                continue;
            }
            if (abdominal && (normalized.equals("消化内科") || normalized.equals("普外科") || normalized.equals("全科医学科"))) {
                departments.add(normalized);
                continue;
            }
            if (urinary && (normalized.equals("泌尿外科") || normalized.equals("肾内科"))) {
                departments.add(normalized);
                continue;
            }
            if (pregnancy && normalized.equals("妇产科")) {
                departments.add(normalized);
                continue;
            }
            if (chest && normalized.equals("心内科")) {
                departments.add(normalized);
                continue;
            }
            if (chemo && (normalized.equals("肿瘤科") || normalized.equals("感染科"))) {
                departments.add(normalized);
            }
        }
        if (departments.isEmpty()) {
            addFallbackDepartments(departments, text, chunks);
        }
        String reason = "Departments selected by primary symptom and patient evidence"
                + (emergency ? "; emergency department added by activated red flag" : "; no emergency department without activated red flag");
        return new DepartmentDecision(List.copyOf(departments), reason);
    }

    private void addFallbackDepartments(LinkedHashSet<String> departments, String text, List<RagChunk> chunks) {
        if (containsAny(text, "腹痛", "腹胀", "肚")) {
            departments.add("消化内科");
            return;
        }
        if (containsAny(text, "胸痛", "胸闷")) {
            departments.add("心内科");
            return;
        }
        if (containsAny(text, "咳嗽", "咳痰", "喘")) {
            departments.add("呼吸内科");
            return;
        }
        if (chunks != null) {
            for (RagChunk chunk : chunks) {
                if (chunk != null && chunk.relatedDepartments() != null) {
                    splitAndAdd(departments, chunk.relatedDepartments(), 2);
                    if (!departments.isEmpty()) {
                        return;
                    }
                }
            }
        }
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private boolean hasPregnancyEvidence(String text) {
        if (containsAny(text, "男性", "男", "未孕", "没有怀孕", "否认怀孕", "非孕")) {
            return false;
        }
        return containsAny(text, "怀孕", "孕妇", "孕期", "孕周", "产后", "胎动", "孕产妇");
    }

    private record RulePattern(String name, List<String> ruleKeywords, List<String> positiveKeywords, List<String> negativeKeywords) {
        boolean matchesRule(String text) {
            return containsAny(text, ruleKeywords);
        }

        boolean hasPositiveEvidence(String text) {
            return containsAny(text, positiveKeywords);
        }

        boolean hasNegativeEvidence(String text) {
            return containsAny(text, negativeKeywords);
        }

        private boolean containsAny(String text, List<String> keywords) {
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
    }

    private record RedFlagDecision(
            List<String> activatedRedFlags,
            List<String> inactiveRedFlags,
            List<String> activationEvidence,
            String reason
    ) {
    }

    private record DepartmentDecision(List<String> departments, String reason) {
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
