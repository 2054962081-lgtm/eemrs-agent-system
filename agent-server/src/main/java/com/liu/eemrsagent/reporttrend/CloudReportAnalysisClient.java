package com.liu.eemrsagent.reporttrend;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liu.eemrsagent.llm.LlmChatRequest;
import com.liu.eemrsagent.llm.LlmChatResponse;
import com.liu.eemrsagent.llm.LlmClientFactory;
import com.liu.eemrsagent.llm.LlmException;
import com.liu.eemrsagent.llm.LlmMessage;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CloudReportAnalysisClient {
    public static final String PROMPT_VERSION = "report-trend-cloud-v1";

    private final LlmClientFactory llmClientFactory;
    private final ObjectMapper objectMapper;

    public CloudReportAnalysisClient(LlmClientFactory llmClientFactory, ObjectMapper objectMapper) {
        this.llmClientFactory = llmClientFactory;
        this.objectMapper = objectMapper;
    }

    public CloudResult analyze(Object cloudPayload) {
        String payloadJson = toJson(cloudPayload);
        LlmChatRequest request = new LlmChatRequest(
                List.of(
                        new LlmMessage("system", systemPrompt()),
                        new LlmMessage("user", payloadJson)
                ),
                "report_trend_cloud_analysis",
                0.2,
                0.8,
                2048,
                true,
                false
        );
        try {
            LlmChatResponse response = llmClientFactory.chatForPurpose("report_trend_cloud_analysis", request);
            return new CloudResult(parse(response.content()), response.model(), response.promptTokens(), response.completionTokens(), response.totalTokens());
        } catch (LlmException e) {
            throw new ReportTrendException(ReportTrendErrorCode.CLOUD_MODEL_FAILED, "Cloud model call failed", e);
        }
    }

    public CloudReportResponse parse(String content) {
        try {
            CloudReportResponse response = objectMapper.readValue(content, CloudReportResponse.class);
            if (isBlank(response.doctorSummary()) || isBlank(response.patientExplanation())) {
                throw new ReportTrendException(ReportTrendErrorCode.CLOUD_RESPONSE_INVALID, "Cloud response misses required summaries");
            }
            return response;
        } catch (JsonProcessingException e) {
            throw new ReportTrendException(ReportTrendErrorCode.CLOUD_RESPONSE_INVALID, "Cloud response is not valid JSON", e);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new ReportTrendException(ReportTrendErrorCode.CLOUD_MODEL_FAILED, "Failed to serialize cloud payload", e);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String systemPrompt() {
        return """
                You are a medical report trend summarization assistant. Return only valid JSON with fields:
                doctorSummary, patientExplanation, contextualInterpretation, keyAbnormalItems, contextLinks,
                riskNotes, followUpQuestions, suggestedDepartment, suggestedAction.
                Use only the desensitized structured indicator trend payload and the desensitized context fields:
                symptom_context_summary, health_context_summary, triage_context_summary.
                Combine report trends, abnormal indicators, current symptom tags, chronic disease tags, and recommended department
                when producing contextLinks and contextualInterpretation.
                Do not diagnose, prescribe, recommend specific drugs, generate a treatment plan, exaggerate risk,
                or claim to replace a doctor. Use cautious wording such as "提示", "可能相关", "建议结合症状", and "建议咨询医生".
                """;
    }

    public record CloudResult(CloudReportResponse response, String modelName, Integer promptTokens, Integer completionTokens, Integer totalTokens) {
    }
}
