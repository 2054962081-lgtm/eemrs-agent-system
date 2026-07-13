package com.liu.eemrsagent.agent;

import com.liu.eemrsagent.llm.LlmChatRequest;
import com.liu.eemrsagent.llm.LlmChatResponse;
import com.liu.eemrsagent.llm.LlmClientFactory;
import com.liu.eemrsagent.llm.LlmException;
import com.liu.eemrsagent.llm.LlmMessage;
import com.liu.eemrsagent.llm.LlmProviderType;
import com.liu.eemrsagent.rag.RagContextFormatter;
import com.liu.eemrsagent.rag.RagRetrievalResult;
import com.liu.eemrsagent.rag.MustAskCoveragePostProcessor;
import com.liu.eemrsagent.rag.QuestionPlan;
import com.liu.eemrsagent.rag.QuestionPlanBuilder;
import com.liu.eemrsagent.rag.RagPromptBuilder;
import com.liu.eemrsagent.rag.RagProperties;
import com.liu.eemrsagent.rag.RagRetrievalClient;
import com.liu.eemrsagent.trace.AgentTraceRecorder;
import com.liu.eemrsagent.trace.TraceErrorCode;
import com.liu.eemrsagent.trace.TraceRunScope;
import com.liu.eemrsagent.trace.TraceRunStart;
import com.liu.eemrsagent.trace.TraceStepData;
import com.liu.eemrsagent.trace.TraceStepScope;
import com.liu.eemrsagent.trace.TraceStepType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class PreConsultationService {

    private final LlmClientFactory llmClientFactory;
    private final RagRetrievalClient ragRetrievalClient;
    private final RagContextFormatter ragContextFormatter;
    private final RagPromptBuilder ragPromptBuilder;
    private final RagProperties ragProperties;
    private final QuestionPlanBuilder questionPlanBuilder;
    private final MustAskCoveragePostProcessor postProcessor;
    private final AgentTraceRecorder traceRecorder;

    public PreConsultationService(
            LlmClientFactory llmClientFactory,
            RagRetrievalClient ragRetrievalClient,
            RagContextFormatter ragContextFormatter,
            RagPromptBuilder ragPromptBuilder,
            RagProperties ragProperties,
            QuestionPlanBuilder questionPlanBuilder,
            MustAskCoveragePostProcessor postProcessor,
            AgentTraceRecorder traceRecorder
    ) {
        this.llmClientFactory = llmClientFactory;
        this.ragRetrievalClient = ragRetrievalClient;
        this.ragContextFormatter = ragContextFormatter;
        this.ragPromptBuilder = ragPromptBuilder;
        this.ragProperties = ragProperties;
        this.questionPlanBuilder = questionPlanBuilder;
        this.postProcessor = postProcessor;
        this.traceRecorder = traceRecorder;
    }

    public PreConsultationResponse ask(PreConsultationRequest request) {
        String mode = request.normalizedMode();
        String input = request.userInput();
        int round = request.safeRound();
        String purpose = LlmClientFactory.PURPOSE_PRE_CONSULTATION;
        LlmProviderType provider = llmClientFactory.providerForPurpose(purpose);
        try (TraceRunScope run = traceRecorder.startRun(new TraceRunStart(
                request.sessionId(),
                request.sessionId(),
                "deep-preconsultation-agent",
                mode + "-pre-consultation",
                "preconsultation-" + mode + "-v1",
                "medical-rag-v1",
                null,
                Map.of("mode", mode, "round", round, "history_size", request.safeHistory().size())
        ))) {
            recordUserInput(input, mode, round);
            recordSessionState(request);
            RagState ragState = retrieveRag(input, mode);
            List<LlmMessage> messages = buildMessages(request, mode, input, round, ragState.ragContext(), ragState.questionPlan());
            LlmChatRequest chatRequest = new LlmChatRequest(
                    messages,
                    purpose,
                    0.2,
                    0.8,
                    2048,
                    false,
                    false
            );
            recordModelRequest(chatRequest, provider);

            LlmChatResponse response;
            try {
                response = llmClientFactory.chatForPurpose(purpose, chatRequest);
            } catch (LlmException e) {
                traceRecorder.startStep(TraceStepType.MODEL_RESPONSE, "model call failed", null, null)
                        .fail(TraceErrorCode.MODEL_HTTP_ERROR.name(), e.getMessage());
                run.fail(TraceErrorCode.MODEL_HTTP_ERROR.name(), e.getMessage());
                return PreConsultationResponse.fail(mode, round, "", provider.value(), e.getMessage());
            }
            run.updateModel(response.model());
            recordModelResponse(response);

            MustAskCoveragePostProcessor.PostProcessResult postProcess;
            try (TraceStepScope step = traceRecorder.startStep(TraceStepType.POST_PROCESS, "must ask coverage post process",
                    response.content(), Map.of("question_plan_has_content", ragState.questionPlan().hasContent()))) {
                postProcess = postProcessor.process(response.content(), ragState.questionPlan());
                step.success(TraceStepData.of(response.content(), postProcess.reply(), Map.of(
                        "coverage_before", postProcess.coverageBefore(),
                        "coverage_after", postProcess.coverageAfter(),
                        "added_question_count", postProcess.addedQuestionCount(),
                        "used_post_process", postProcess.usedPostProcess()
                )));
            } catch (RuntimeException e) {
                run.fail(TraceErrorCode.POST_PROCESS_FAILED.name(), e.getMessage());
                throw e;
            }
            String reply = postProcess.reply();
            boolean finished = isFinished(mode, round, input, reply);
            recordFollowUpDecision(finished, mode, round, postProcess);
            PreConsultationResponse finalResponse = PreConsultationResponse.ok(
                    mode,
                    reply,
                    finished,
                    round,
                    extractRecommendedDepartment(reply),
                    extractUrgency(reply),
                    response.model(),
                    response.provider(),
                    new PreConsultationRagDebug(
                            ragState.questionPlan().keyQuestions(),
                            ragState.questionPlan().redFlags(),
                            ragState.questionPlan().urgencyLevel(),
                            ragState.questionPlan().recommendedDepartments(),
                            ragState.retrievalResult().expandedQuery(),
                            ragState.retrievalResult().docTypeCounts(),
                            postProcess.coverageBefore(),
                            postProcess.coverageAfter(),
                            postProcess.addedQuestionCount(),
                            ragState.questionPlan().hasContent(),
                            ragState.retrievalResult().usedQueryExpansion(),
                            postProcess.usedPostProcess()
                    )
            );
            recordFinalAnswer(finalResponse);
            run.success(reply, response.promptTokens(), response.completionTokens(), response.totalTokens());
            return finalResponse;
        }
    }

    private List<LlmMessage> buildMessages(
            PreConsultationRequest request,
            String mode,
            String input,
            int round,
            String ragContext,
            QuestionPlan questionPlan
    ) {
        List<LlmMessage> messages = new ArrayList<>();
        String memoryContext = formatMemoryContext(request.memoryContext());
        String basePrompt = buildSystemPrompt(mode, round) + memoryContext;
        messages.add(new LlmMessage("system", ragPromptBuilder.mergeSystemPrompt(basePrompt, ragContext, questionPlan)));
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

    private String formatMemoryContext(PreConsultationRequest.MemoryContext memoryContext) {
        if (memoryContext == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("\n\n【长期健康档案】\n");
        appendMap(builder, memoryContext.longTermMemory(), 12);
        builder.append("\n【近期就诊记忆】\n");
        appendList(builder, memoryContext.mediumTermMemory(), 8);
        builder.append("\n【本次问诊状态】\n");
        appendMap(builder, memoryContext.shortTermMemory(), 12);
        builder.append("\n【用户历史相似记忆】\n");
        appendList(builder, memoryContext.relatedUserMemory(), 5);
        builder.append("\n【医学知识库 RAG】\n医学知识库内容见下方 RAG 检索知识分区。");
        return truncate(builder.toString(), 5000);
    }

    private void appendMap(StringBuilder builder, Map<String, Object> data, int maxItems) {
        if (data == null || data.isEmpty()) {
            builder.append("- 未记录\n");
            return;
        }
        int count = 0;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (count >= maxItems) {
                break;
            }
            Object value = entry.getValue();
            if (value == null || String.valueOf(value).isBlank()) {
                continue;
            }
            builder.append("- ").append(entry.getKey()).append("：")
                    .append(truncate(String.valueOf(value), 500)).append("\n");
            count++;
        }
        if (count == 0) {
            builder.append("- 未记录\n");
        }
    }

    private void appendList(StringBuilder builder, List<Map<String, Object>> items, int maxItems) {
        if (items == null || items.isEmpty()) {
            builder.append("- 未记录\n");
            return;
        }
        int count = 0;
        for (Map<String, Object> item : items) {
            if (item == null || count >= maxItems) {
                continue;
            }
            Object summary = item.get("summary");
            if (summary == null) {
                summary = item.get("text");
            }
            if (summary == null) {
                summary = item;
            }
            builder.append(count + 1).append(". ")
                    .append(truncate(String.valueOf(summary), 700)).append("\n");
            count++;
        }
        if (count == 0) {
            builder.append("- 未记录\n");
        }
    }

    private RagState retrieveRag(String input, String mode) {
        String scene = "deep".equals(mode) ? RagRetrievalClient.SCENE_DEEP_INQUIRY : RagRetrievalClient.SCENE_PRE_INQUIRY;
        String expandedOrRaw = input == null ? "" : input;
        try (TraceStepScope ignored = traceRecorder.startStep(TraceStepType.RAG_QUERY_BUILD, "build rag query",
                Map.of("input_hash_only", true, "mode", mode), Map.of("scene", scene))) {
            ignored.success(Map.of("scene", scene, "top_k", ragProperties.getTopK()));
        }
        RagRetrievalResult result;
        try (TraceStepScope step = traceRecorder.startStep(TraceStepType.RAG_REQUEST, "call rag retrieval service",
                Map.of("scene", scene, "query", expandedOrRaw), Map.of("service_url", ragProperties.getServiceUrl()))) {
            result = ragRetrievalClient.retrieveWithMetadata(input, scene);
            step.success(TraceStepData.of(null, Map.of(
                    "result_count", result.chunks().size(),
                    "used_query_expansion", result.usedQueryExpansion(),
                    "expanded_query", result.expandedQuery()
            ), Map.of("doc_type_counts", result.docTypeCounts())));
        }
        try (TraceStepScope step = traceRecorder.startStep(TraceStepType.RAG_RETRIEVAL, "rag retrieval results",
                null, Map.of("scene", scene))) {
            step.success(TraceStepData.of(null, result.chunks().stream()
                    .map(chunk -> Map.of(
                            "chunk_id", chunk.chunkId(),
                            "doc_type", chunk.docType(),
                            "score", chunk.score(),
                            "title", chunk.title()))
                    .toList(), Map.of(
                    "result_count", result.chunks().size(),
                    "doc_type_counts", result.docTypeCounts(),
                    "rag_version", "medical-rag-v1"
            )));
        }
        String context = ragContextFormatter.format(result.chunks(), ragProperties.getMaxContextChars());
        QuestionPlan questionPlan;
        try (TraceStepScope step = traceRecorder.startStep(TraceStepType.QUESTION_PLAN, "build question plan",
                Map.of("chunk_count", result.chunks().size(), "mode", mode), null)) {
            questionPlan = questionPlanBuilder.build(result.chunks(), mode, input);
            step.success(TraceStepData.of(null, questionPlan, Map.of(
                    "has_content", questionPlan.hasContent(),
                    "key_question_count", questionPlan.keyQuestions().size(),
                    "red_flag_count", questionPlan.redFlags().size()
            )));
        }
        return new RagState(context, questionPlan, result);
    }

    private void recordUserInput(String input, String mode, int round) {
        try (TraceStepScope step = traceRecorder.startStep(TraceStepType.USER_INPUT, "pre-consultation user input",
                Map.of("input", input == null ? "" : input), Map.of("mode", mode, "round", round))) {
            step.success(Map.of("input_length", input == null ? 0 : input.length()));
        }
    }

    private void recordSessionState(PreConsultationRequest request) {
        try (TraceStepScope step = traceRecorder.startStep(TraceStepType.SESSION_STATE, "read conversation state",
                null, Map.of("history_size", request.safeHistory().size()))) {
            step.success(Map.of(
                    "history_size", request.safeHistory().size(),
                    "has_memory_context", request.memoryContext() != null
            ));
        }
    }

    private void recordModelRequest(LlmChatRequest chatRequest, LlmProviderType provider) {
        try (TraceStepScope step = traceRecorder.startStep(TraceStepType.MODEL_REQUEST, "build model request",
                Map.of("message_count", chatRequest.messages().size(), "purpose", chatRequest.purpose()),
                Map.of("provider", provider.value(), "stream", chatRequest.stream()))) {
            step.success(Map.of("max_tokens", chatRequest.maxTokens(), "temperature", chatRequest.temperature()));
        }
    }

    private void recordModelResponse(LlmChatResponse response) {
        try (TraceStepScope step = traceRecorder.startStep(TraceStepType.MODEL_RESPONSE, "model response",
                null, Map.of("provider", response.provider(), "model", response.model()))) {
            step.success(new TraceStepData(
                    null,
                    response.content(),
                    Map.of("content_length", response.content() == null ? 0 : response.content().length()),
                    response.model(),
                    null,
                    response.promptTokens(),
                    response.completionTokens(),
                    response.totalTokens()
            ));
        }
    }

    private void recordFollowUpDecision(boolean finished, String mode, int round,
                                        MustAskCoveragePostProcessor.PostProcessResult postProcess) {
        try (TraceStepScope step = traceRecorder.startStep(TraceStepType.FOLLOW_UP_DECISION, "follow-up decision",
                null, Map.of("mode", mode, "round", round))) {
            step.success(Map.of(
                    "need_follow_up", !finished,
                    "reason_code", finished ? "ENOUGH_INFORMATION_OR_ROUND_LIMIT" : "NEED_MORE_INFORMATION",
                    "coverage_after", postProcess.coverageAfter()
            ));
        }
    }

    private void recordFinalAnswer(PreConsultationResponse response) {
        try (TraceStepScope step = traceRecorder.startStep(TraceStepType.FINAL_ANSWER, "final answer",
                null, Map.of("finished", response.finished()))) {
            step.success(Map.of(
                    "finished", response.finished(),
                    "recommended_department", response.recommendedDepartment(),
                    "urgency", response.urgency(),
                    "reply", response.reply()
            ));
        }
    }

    private record RagState(String ragContext, QuestionPlan questionPlan, RagRetrievalResult retrievalResult) {
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
