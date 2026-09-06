package com.liu.eemrsagent.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionPlanBuilderTest {

    private final QuestionPlanBuilder builder = new QuestionPlanBuilder(new RagProperties());

    @Test
    void redFlagRuleWithoutPositiveEvidenceDoesNotEscalateToEmergency() {
        QuestionPlan plan = builder.build(List.of(
                abdominalPainChunk(),
                giBleedingRule()
        ), "quick", "主诉: 腹痛。已知: 持续1天 / 中等疼痛 / 逐渐加重 / 无便血 / 无黑便 / 无呕血。任务: 预问诊分诊");

        assertThat(plan.availableRedFlagRules()).isNotEmpty();
        assertThat(plan.activatedRedFlags()).isEmpty();
        assertThat(plan.redFlags()).isEmpty();
        assertThat(plan.riskLevel()).isNotEqualTo("high");
        assertThat(plan.urgencyLevel()).doesNotContain("120", "立即");
        assertThat(plan.inactiveRedFlags()).anyMatch(item -> item.contains("GI bleeding"));
    }

    @Test
    void abdominalPainWithBlackStoolActivatesGiBleedingRisk() {
        QuestionPlan plan = builder.build(List.of(
                abdominalPainChunk(),
                giBleedingRule()
        ), "quick", "主诉: 腹痛伴黑便，今天头晕心慌。任务: 预问诊分诊");

        assertThat(plan.availableRedFlagRules()).isNotEmpty();
        assertThat(plan.activatedRedFlags()).contains("GI bleeding");
        assertThat(plan.riskLevel()).isEqualTo("high");
        assertThat(plan.urgencyLevel()).isEqualTo("emergency");
        assertThat(plan.recommendedDepartments()).contains("急诊科", "消化内科");
    }

    @Test
    void pregnancyRuleDoesNotActivateForMalePatient() {
        QuestionPlan plan = builder.build(List.of(
                abdominalPainChunk(),
                pregnancyRule()
        ), "quick", "男性，腹痛持续1天，无阴道出血。任务: 预问诊分诊");

        assertThat(plan.activatedRedFlags()).doesNotContain("Pregnancy red flag");
        assertThat(plan.inactiveRedFlags()).anyMatch(item -> item.contains("Pregnancy red flag"));
        assertThat(plan.riskLevel()).isNotEqualTo("high");
        assertThat(plan.recommendedDepartments()).doesNotContain("妇产科");
    }

    @Test
    void pregnantAbdominalPainWithVaginalBleedingActivatesPregnancyRisk() {
        QuestionPlan plan = builder.build(List.of(
                abdominalPainChunk(),
                pregnancyRule()
        ), "quick", "怀孕10周，腹痛伴阴道出血。任务: 预问诊分诊");

        assertThat(plan.activatedRedFlags()).contains("Pregnancy red flag");
        assertThat(plan.riskLevel()).isEqualTo("high");
        assertThat(plan.recommendedDepartments()).contains("急诊科", "妇产科");
    }

    @Test
    void chestPainWithDiaphoresisActivatesChestPainRisk() {
        QuestionPlan plan = builder.build(List.of(chestPainRule()), "quick", "胸痛胸闷半小时，伴大汗，休息不缓解。任务: 预问诊分诊");

        assertThat(plan.activatedRedFlags()).contains("Chest pain red flag");
        assertThat(plan.riskLevel()).isEqualTo("high");
        assertThat(plan.recommendedDepartments()).contains("急诊科", "心内科");
    }

    @Test
    void unknownRedFlagRulePromptsFollowUpButDoesNotHit() {
        QuestionPlan plan = builder.build(List.of(giBleedingRule()), "quick", "腹痛，想知道挂什么科。任务: 预问诊分诊");

        assertThat(plan.availableRedFlagRules()).isNotEmpty();
        assertThat(plan.activatedRedFlags()).isEmpty();
        assertThat(plan.inactiveRedFlags()).anyMatch(item -> item.contains("UNKNOWN"));
        assertThat(plan.keyQuestions()).anyMatch(item -> item.contains("黑便") || item.contains("便血") || item.contains("呕血"));
        assertThat(plan.riskLevel()).isNotEqualTo("high");
    }

    @Test
    void abdominalWithoutSpecialEvidenceDoesNotExpandDepartments() {
        QuestionPlan plan = builder.build(List.of(
                abdominalPainChunk(),
                giBleedingRule(),
                pregnancyRule(),
                urinaryChunk(),
                chemoPopulationChunk(),
                chestPainRule()
        ), "quick", "主诉: 腹痛。已知: 持续1天 / 中等疼痛 / 逐渐加重 / 否认明显伴随症状。任务: 预问诊分诊");

        assertThat(plan.recommendedDepartments()).contains("消化内科");
        assertThat(plan.recommendedDepartments()).doesNotContain("心内科", "肿瘤科", "泌尿外科", "妇产科", "肾内科");
    }

    @Test
    void urinaryEvidenceAllowsUrinaryDepartments() {
        QuestionPlan plan = builder.build(List.of(
                abdominalPainChunk(),
                urinaryChunk()
        ), "quick", "腹痛伴尿频尿痛，持续1天。任务: 预问诊分诊");

        assertThat(plan.recommendedDepartments()).contains("消化内科", "泌尿外科");
    }

    @Test
    void pregnancyEvidenceAllowsObstetricDepartment() {
        QuestionPlan plan = builder.build(List.of(
                abdominalPainChunk(),
                pregnancyRule()
        ), "quick", "怀孕10周腹痛。任务: 预问诊分诊");

        assertThat(plan.recommendedDepartments()).contains("妇产科");
    }

    private RagChunk abdominalPainChunk() {
        return chunk(
                "SYM_ABDOMINAL_PAIN_001_CHUNK_001",
                "SYM_ABDOMINAL_PAIN_001",
                "symptom_inquiry",
                "腹痛问诊模板",
                "尽快就医",
                "消化内科，普外科，妇产科，急诊科",
                "儿童，成人，老年人，孕妇",
                "腹痛，腹胀，上腹痛，下腹痛",
                List.of("腹痛开始时间、部位、性质和程度", "是否伴发热、呕吐、腹泻、黑便、便血或黄疸"),
                List.of("剧烈持续腹痛或腹肌紧张", "腹痛伴呕血黑便或便血")
        );
    }

    private RagChunk giBleedingRule() {
        return chunk(
                "RED_GI_BLEEDING_001_CHUNK_001",
                "RED_GI_BLEEDING_001",
                "red_flag",
                "消化道出血红旗风险规则",
                "立即拨打120",
                "急诊科，消化内科",
                "成人，老年人，抗凝药使用者",
                "呕血，黑便，便血",
                List.of("是否有呕血、黑便或便血", "是否头晕心慌出冷汗"),
                List.of("呕血或柏油样黑便", "便血量多", "头晕心慌出冷汗或晕厥")
        );
    }

    private RagChunk pregnancyRule() {
        return chunk(
                "RED_PREGNANCY_001_CHUNK_001",
                "RED_PREGNANCY_001",
                "red_flag",
                "孕产妇红旗风险规则",
                "立即拨打120",
                "妇产科，急诊科",
                "孕妇，产妇",
                "腹痛，阴道出血，头痛，胎动减少",
                List.of("孕周是多少", "是否阴道出血或胎动减少"),
                List.of("孕早期腹痛伴阴道出血", "胎动明显减少")
        );
    }

    private RagChunk chestPainRule() {
        return chunk(
                "RED_CHEST_PAIN_001_CHUNK_001",
                "RED_CHEST_PAIN_001",
                "red_flag",
                "胸痛红旗风险规则",
                "立即拨打120",
                "急诊科，心内科",
                "成人，老年人，冠心病患者",
                "胸痛，胸闷，大汗",
                List.of("胸痛是否持续不缓解", "是否伴大汗、气短、恶心"),
                List.of("胸痛伴大汗、气短、恶心呕吐或放射痛", "胸痛持续超过数分钟不缓解")
        );
    }

    private RagChunk urinaryChunk() {
        return chunk(
                "SYM_URINARY_001_CHUNK_001",
                "SYM_URINARY_001",
                "symptom_inquiry",
                "泌尿系统症状问诊模板",
                "普通门诊",
                "泌尿外科，肾内科，妇产科，急诊科",
                "成人，老年人，孕妇，肾功能不全患者",
                "尿频，尿急，尿痛，血尿，腰痛",
                List.of("尿频尿急尿痛持续多久", "是否发热、寒战、腰痛或肉眼血尿"),
                List.of("发热寒战伴腰痛", "肉眼血尿伴血块或尿潴留")
        );
    }

    private RagChunk chemoPopulationChunk() {
        return chunk(
                "POP_CHEMO_001_CHUNK_001",
                "POP_CHEMO_001",
                "special_population",
                "化疗患者问诊要点",
                "根据红旗信号判断",
                "肿瘤科，急诊科，感染科",
                "化疗患者，肿瘤患者",
                "发热，口腔溃疡，呼吸困难，腹泻",
                List.of("最近一次化疗时间和方案大概是什么"),
                List.of("化疗后发热")
        );
    }

    private RagChunk chunk(
            String chunkId,
            String docId,
            String docType,
            String title,
            String urgency,
            String departments,
            String population,
            String symptoms,
            List<String> mustAsk,
            List<String> redFlags
    ) {
        return new RagChunk(
                chunkId,
                docId,
                docType,
                title,
                urgency,
                departments,
                population,
                symptoms,
                mustAsk,
                redFlags,
                List.of(),
                List.of(),
                List.of(),
                0.8,
                title
        );
    }
}
