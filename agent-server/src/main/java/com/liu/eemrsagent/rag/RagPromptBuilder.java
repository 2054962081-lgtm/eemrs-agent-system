package com.liu.eemrsagent.rag;

import org.springframework.stereotype.Component;

@Component
public class RagPromptBuilder {

    public String mergeSystemPrompt(String basePrompt, String ragContext) {
        if (ragContext == null || ragContext.isBlank()) {
            return basePrompt;
        }
        return basePrompt + """


                【RAG 使用原则】
                你需要结合用户输入和 RAG 检索知识，生成安全、简洁、可执行的预问诊回复。
                RAG 检索知识只用于辅助问诊、分诊和病历摘要，不代表最终诊断。
                如果 RAG 内容与用户问题无关，请忽略无关内容，不要强行引用。
                如果存在红旗风险，应优先提示急诊或拨打 120。
                即使建议急诊，也要补充 3-5 个最关键追问，帮助医生快速了解病情。
                不要机械复述知识库大段内容，不要编造知识库中没有的信息。
                不要开具处方、给出具体药物剂量，或让用户自行调整处方药。
                儿童、孕妇、老人、慢病患者、免疫低下患者、自伤风险用户需要更谨慎。

                输出建议包含：
                1. 风险判断；
                2. 建议就诊科室和紧急程度；
                3. 3-5 个最关键追问；
                4. 需要告知医生的信息；
                5. 安全提醒。

                """ + ragContext;
    }
}
