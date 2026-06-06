package com.liu.eemrsagent.rag;

import org.springframework.stereotype.Component;

@Component
public class RagPromptBuilder {

    public String mergeSystemPrompt(String basePrompt, String ragContext) {
        return mergeSystemPrompt(basePrompt, ragContext, null);
    }

    public String mergeSystemPrompt(String basePrompt, String ragContext, QuestionPlan questionPlan) {
        String prompt = basePrompt == null ? "" : basePrompt;
        if (ragContext != null && !ragContext.isBlank()) {
            prompt = prompt + """


                    【RAG 使用原则】
                    你需要结合用户输入和 RAG 检索知识，生成安全、简洁、可执行的预问诊回复。
                    RAG 检索知识只用于辅助问诊、分诊和病历摘要，不代表最终诊断。
                    如果 RAG 内容与用户问题无关，请忽略无关内容，不要强行引用。
                    如果存在红旗风险，应优先提示急诊或拨打 120。
                    即使建议急诊，也要补充 3-5 个最关键追问，帮助医生快速了解病情。
                    不要机械复述知识库大段内容，不要编造知识库中没有的信息。
                    不要开具处方、给出具体药物剂量，或让用户自行调整处方药。
                    儿童、孕妇、老人、慢病患者、免疫低下患者、自伤风险用户需要更谨慎。

                    【RAG 检索知识】
                    """ + ragContext;
        }
        return appendQuestionPlan(prompt, questionPlan);
    }

    private String appendQuestionPlan(String prompt, QuestionPlan questionPlan) {
        if (questionPlan == null || !questionPlan.hasContent()) {
            return prompt;
        }
        return prompt + """


                【RAG 问诊计划】
                下面是从检索知识中提取的结构化问诊计划。回复时必须优先覆盖这些关键点，但要自然融入对话，不要机械罗列。
                风险等级：%s
                紧急度：%s
                推荐科室：%s
                必须追问/确认的关键问题：%s
                需要识别的红旗风险：%s
                禁止行为：%s
                期望回答要点：%s
                医生病历需记录字段：%s
                证据标题：%s

                【硬性约束】
                1. 如存在急诊或 120 风险，先给就医优先级，再补充关键追问。
                2. 快速问诊优先覆盖最重要的 3-5 个问题，深度问诊最多覆盖 8 个关键问题。
                3. 不要给出具体处方剂量，不要建议自行停药或拖延就医。
                4. 若缺少关键问题答案，要明确提出问题，避免只做泛泛提醒。
                """.formatted(
                safe(questionPlan.riskLevel()),
                safe(questionPlan.urgencyLevel()),
                String.join("；", questionPlan.recommendedDepartments()),
                String.join("；", questionPlan.keyQuestions()),
                String.join("；", questionPlan.redFlags()),
                String.join("；", questionPlan.forbiddenActions()),
                String.join("；", questionPlan.expectedResponsePoints()),
                String.join("；", questionPlan.doctorRecordFields()),
                String.join("；", questionPlan.evidenceTitles())
        );
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
