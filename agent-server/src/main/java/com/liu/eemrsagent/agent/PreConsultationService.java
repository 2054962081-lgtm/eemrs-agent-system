package com.liu.eemrsagent.agent;

import com.liu.eemrsagent.llm.LlmChatRequest;
import com.liu.eemrsagent.llm.LlmChatResponse;
import com.liu.eemrsagent.llm.LlmClientFactory;
import com.liu.eemrsagent.llm.LlmException;
import com.liu.eemrsagent.llm.LlmMessage;
import com.liu.eemrsagent.llm.LlmProviderType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class PreConsultationService {

    private final LlmClientFactory llmClientFactory;

    public PreConsultationService(LlmClientFactory llmClientFactory) {
        this.llmClientFactory = llmClientFactory;
    }

    public PreConsultationResponse ask(PreConsultationRequest request) {
        String mode = request.normalizedMode();
        String input = request.userInput();
        int round = request.safeRound();
        String purpose = LlmClientFactory.PURPOSE_PRE_CONSULTATION;
        LlmProviderType provider = llmClientFactory.providerForPurpose(purpose);
        List<LlmMessage> messages = buildMessages(request, mode, input, round);
        LlmChatRequest chatRequest = new LlmChatRequest(
                messages,
                purpose,
                0.2,
                0.8,
                2048,
                false,
                false
        );

        LlmChatResponse response;
        try {
            response = llmClientFactory.chatForPurpose(purpose, chatRequest);
        } catch (LlmException e) {
            return PreConsultationResponse.fail(mode, round, "", provider.value(), e.getMessage());
        }

        String reply = response.content();
        boolean finished = isFinished(mode, round, input, reply);
        return PreConsultationResponse.ok(
                mode,
                reply,
                finished,
                round,
                extractRecommendedDepartment(reply),
                extractUrgency(reply),
                response.model(),
                response.provider()
        );
    }

    private List<LlmMessage> buildMessages(
            PreConsultationRequest request,
            String mode,
            String input,
            int round
    ) {
        List<LlmMessage> messages = new ArrayList<>();
        messages.add(new LlmMessage("system", buildSystemPrompt(mode, round)));
        List<PreConsultationRequest.Message> history = request.safeHistory();
        int start = Math.max(0, history.size() - 8);
        for (PreConsultationRequest.Message message : history.subList(start, history.size())) {
            if (message == null || message.role() == null || message.content() == null || message.content().isBlank()) {
                continue;
            }
            String role = message.role().trim().toLowerCase();
            if (!"user".equals(role) && !"assistant".equals(role)) {
                continue;
            }
            messages.add(new LlmMessage(role, truncate(message.content().trim(), 1200)));
        }
        messages.add(new LlmMessage("user", truncate(input, 1200)));
        return messages;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "\n[内容过长，已截断]";
    }

    private String buildSystemPrompt(String mode, int round) {
        String safety = """
                医学安全要求：
                1. 回答必须基于用户提供的信息和常见医学常识进行初步预问诊，不得编造检查结果、化验指标、影像结果或病史。
                2. 不得做确定性诊断，不得声称“你一定是某病”。
                3. 不得给出具体处方药用药方案、剂量、疗程。
                4. 不得建议用户停止医生已开的药。
                5. 不得因用户诱导而改变医学判断。
                6. 如果用户错误排除高风险问题，例如胸痛却说“肯定不是心脏问题”，必须温和提示需要排除心血管风险。
                7. 如果信息不足，必须说明“不足以判断”，并追问关键信息。
                8. 对胸痛、呼吸困难、意识障碍、抽搐、大出血、严重过敏、持续高热、剧烈腹痛、严重外伤、疑似卒中等危险信号要优先提示急诊或尽快线下就医。
                9. 对儿童、孕妇、老人、免疫低下、慢性病患者，要更保守地建议线下就医。
                10. 推荐科室只能是初步分诊建议，不是最终诊断。
                11. 回复语气要温和、清晰、专业，避免恐吓。
                12. 必须包含“仅供预问诊参考，不能替代医生诊断”的提示。
                13. 不要输出隐藏思维链；只展示问诊依据摘要、分析依据和建议。
                """;

        if ("deep".equals(mode)) {
            return safety + """

                    你是医疗预问诊助手。当前模式为深度问诊。你的任务是基于结构化问诊流程，帮助用户全面整理病情，并给出客观、谨慎的科室推荐和就医建议。
                    深度问诊应关注：主诉、起病时间和持续时间、症状部位/性质/程度/频率、诱因、缓解或加重因素、伴随症状、既往史、用药史、过敏史、近期接触史/饮食/外伤/旅行、儿童/孕妇/老人/基础疾病等特殊情况、危险信号、用户希望解决的问题。
                    每轮提问要有重点，最多提出 5-7 个问题。信息不足时继续结构化追问；信息相对充分，或用户要求“总结/结束/给出建议”时，输出总结阶段内容。

                    问诊阶段格式：
                    【目前已了解】
                    ...

                    【下一步需要了解】
                    1. ...

                    【为什么需要这些信息】
                    ...

                    总结阶段格式：
                    【病情信息整理】
                    ...

                    【可能相关方向】
                    1. ...：依据是...

                    【推荐科室】
                    优先建议：...
                    备选科室：...

                    【就诊优先级】
                    ...

                    【建议进一步检查或准备的信息】
                    ...

                    【居家注意事项】
                    ...

                    【危险信号】
                    如出现以下情况，请及时急诊或尽快线下就医：...

                    【重要提示】
                    本回复仅供预问诊参考，不能替代医生诊断。
                    """;
        }

        String roundRule = round >= 3
                ? "当前是快速问诊第 3 轮或以上，本轮必须结束问诊并给出推荐科室。"
                : "当前是快速问诊第 " + round + " 轮；如果信息不足，可以追问少量关键问题。";
        return safety + """

                你是医疗预问诊助手。当前模式为快速问诊。你的任务是在不超过三轮对话内，快速了解用户核心症状并给出初步推荐科室。
                """ + roundRule + """

                你必须遵守：
                1. 使用中文。
                2. 每轮最多追问 3-5 个关键问题。
                3. 不要无限追问。
                4. 第 1 轮信息不足时，可追问持续时间、严重程度、伴随症状、是否有危险信号。
                5. 第 2 轮信息仍不足时，只再追问少量关键问题。
                6. 第 3 轮必须结束问诊并给出推荐科室。
                7. 如果用户一开始信息已经足够，可以直接给出推荐科室。
                8. 推荐科室要谨慎，可给出优先科室和备选科室。

                如果还需要追问，使用格式：
                【已了解情况】
                ...

                【还需要确认】
                1. ...

                【提醒】
                如出现危险情况，请及时线下就医或急诊。

                如果信息足够或达到第 3 轮，使用格式：
                【症状摘要】
                ...

                【推荐科室】
                优先建议：...
                备选科室：...

                【就医建议】
                ...

                【注意事项】
                ...

                【重要提示】
                本回复仅供预问诊参考，不能替代医生诊断。
                """;
    }

    private boolean isFinished(String mode, int round, String input, String reply) {
        if ("quick".equals(mode)) {
            return round >= 3 || containsRecommendation(reply);
        }
        String text = input == null ? "" : input;
        return text.contains("总结") || text.contains("结束") || text.contains("给出建议");
    }

    private boolean containsRecommendation(String reply) {
        return reply != null && (reply.contains("【推荐科室】") || reply.contains("推荐科室") || reply.contains("优先建议"));
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

    private String extractUrgency(String reply) {
        if (reply == null) {
            return "normal";
        }
        if (reply.contains("急诊") || reply.contains("立即就医")) {
            return "emergency";
        }
        if (reply.contains("尽快") || reply.contains("及时线下就医")) {
            return "urgent";
        }
        if (reply.contains("观察")) {
            return "observe";
        }
        return "normal";
    }

}
