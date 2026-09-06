package com.liu.eemrsagent.reporttrend;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liu.eemrsagent.llm.LlmChatRequest;
import com.liu.eemrsagent.llm.LlmChatResponse;
import com.liu.eemrsagent.llm.LlmClientFactory;
import com.liu.eemrsagent.llm.LlmException;
import com.liu.eemrsagent.llm.LlmMessage;
import com.liu.eemrsagent.trace.AgentTraceRecorder;
import com.liu.eemrsagent.trace.TraceRedactor;
import com.liu.eemrsagent.trace.TraceStepData;
import com.liu.eemrsagent.trace.TraceStepScope;
import com.liu.eemrsagent.trace.TraceStepType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class CloudReportAnalysisClient {
    public static final String PROMPT_VERSION = "report-trend-cloud-v1";

    private final LlmClientFactory llmClientFactory;
    private final ObjectMapper objectMapper;
    private final AgentTraceRecorder traceRecorder;
    private final TraceRedactor traceRedactor;

    @Autowired
    public CloudReportAnalysisClient(LlmClientFactory llmClientFactory, ObjectMapper objectMapper,
                                     AgentTraceRecorder traceRecorder, TraceRedactor traceRedactor) {
        this.llmClientFactory = llmClientFactory;
        this.objectMapper = objectMapper;
        this.traceRecorder = traceRecorder;
        this.traceRedactor = traceRedactor;
    }

    CloudReportAnalysisClient(LlmClientFactory llmClientFactory, ObjectMapper objectMapper) {
        this(llmClientFactory, objectMapper, null, new TraceRedactor());
    }

    public CloudResult analyze(Object cloudPayload) {
        RawCloudResult raw = request(cloudPayload);
        return new CloudResult(parse(raw.content()), raw.modelName(), raw.promptTokens(), raw.completionTokens(), raw.totalTokens());
    }

    public RawCloudResult request(Object cloudPayload) {
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
            if (response == null || response.content() == null || response.content().isBlank()) {
                throw new ReportTrendException(ReportTrendErrorCode.CLOUD_RESPONSE_EMPTY, "Cloud response is empty");
            }
            recordRawCloudResponse(response);
            return new RawCloudResult(response.content(), response.model(), response.promptTokens(), response.completionTokens(), response.totalTokens());
        } catch (LlmException e) {
            throw new ReportTrendException(classifyModelFailure(e), "Cloud model call failed", e);
        }
    }

    private ReportTrendErrorCode classifyModelFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String name = current.getClass().getName().toLowerCase();
            String message = current.getMessage() == null ? "" : current.getMessage().toLowerCase();
            if (message.contains("rate") || message.contains("429") || message.contains("频率") || message.contains("额度")) {
                return ReportTrendErrorCode.CLOUD_RATE_LIMITED;
            }
            if (message.contains("read timed out") || message.contains("read timeout") || message.contains("响应超时")) {
                return ReportTrendErrorCode.CLOUD_READ_TIMEOUT;
            }
            if (message.contains("connect timed out") || message.contains("connection timed out") || message.contains("connect timeout")) {
                return ReportTrendErrorCode.CLOUD_CONNECT_TIMEOUT;
            }
            if (message.contains("http") || message.contains("502") || message.contains("503") || message.contains("504")
                    || message.contains("5xx") || name.contains("http")) {
                return ReportTrendErrorCode.CLOUD_HTTP_ERROR;
            }
            if (message.contains("empty") || message.contains("返回内容为空")) {
                return ReportTrendErrorCode.CLOUD_RESPONSE_EMPTY;
            }
            current = current.getCause();
        }
        return ReportTrendErrorCode.CLOUD_MODEL_FAILED;
    }

    private void recordRawCloudResponse(LlmChatResponse response) {
        if (traceRecorder == null) {
            return;
        }
        String content = response == null ? null : response.content();
        String modelName = response == null || response.model() == null || response.model().isBlank() ? "UNKNOWN" : response.model();
        try (TraceStepScope step = traceRecorder.startStep(TraceStepType.CLOUD_MODEL_RESPONSE, "cloud model raw response before json parse", null,
                Map.of("model_name", modelName,
                        "raw_response_hash", traceRedactor.stableHash(content),
                        "raw_response_length", content == null ? 0 : content.length()))) {
            step.success(new TraceStepData(null, content,
                    Map.of("model_name", modelName,
                            "raw_response_hash", traceRedactor.stableHash(content),
                            "raw_response_length", content == null ? 0 : content.length()),
                    response == null ? null : response.model(), null,
                    response == null ? null : response.promptTokens(),
                    response == null ? null : response.completionTokens(),
                    response == null ? null : response.totalTokens()));
        }
    }

    public CloudReportResponse parse(String content) {
        return toResponse(parseJson(content));
    }

    public JsonNode parseJson(String content) {
        try {
            return objectMapper.readTree(canonicalJsonText(content));
        } catch (JsonParseException e) {
            throw new ReportTrendException(ReportTrendErrorCode.CLOUD_RESPONSE_JSON_INVALID, "Cloud response is not valid JSON", e);
        } catch (JsonProcessingException e) {
            throw new ReportTrendException(ReportTrendErrorCode.CLOUD_RESPONSE_JSON_INVALID, "Cloud response is not valid JSON", e);
        } catch (IllegalArgumentException e) {
            throw new ReportTrendException(ReportTrendErrorCode.CLOUD_RESPONSE_JSON_INVALID, "Cloud response is not valid JSON", e);
        }
    }

    private String canonicalJsonText(String content) {
        String trimmed = content == null ? "" : content.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstNewline = trimmed.indexOf('\n');
        int fenceEnd = trimmed.lastIndexOf("```");
        if (firstNewline < 0 || fenceEnd <= firstNewline) {
            return trimmed;
        }
        String language = trimmed.substring(3, firstNewline).trim();
        if (!language.isBlank() && !"json".equalsIgnoreCase(language)) {
            return trimmed;
        }
        return trimmed.substring(firstNewline + 1, fenceEnd).trim();
    }

    public CloudReportResponse toResponse(JsonNode json) {
        try {
            return objectMapper.treeToValue(json, CloudReportResponse.class);
        } catch (JsonMappingException e) {
            throw new ReportTrendException(ReportTrendErrorCode.CLOUD_RESPONSE_SCHEMA_MISMATCH, "Cloud response schema mismatch", e);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new ReportTrendException(ReportTrendErrorCode.CLOUD_RESPONSE_SCHEMA_MISMATCH, "Cloud response schema mismatch", e);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new ReportTrendException(ReportTrendErrorCode.CLOUD_MODEL_FAILED, "Failed to serialize cloud payload", e);
        }
    }

    private String systemPrompt() {
        return """
                You are a medical report trend summarization assistant. Return only valid JSON matching this exact contract:
                %s
                Use only the desensitized structured indicator trend payload and the desensitized context fields:
                symptom_context_summary, health_context_summary, triage_context_summary.
                Combine report trends, abnormal indicators, current symptom tags, chronic disease tags, and recommended department
                when producing contextLinks and contextualInterpretation.
                Do not diagnose, prescribe, recommend specific drugs, generate a treatment plan, exaggerate risk,
                or claim to replace a doctor. Use cautious wording such as "提示", "可能相关", "建议结合症状", and "建议咨询医生".
                Return JSON only: no markdown, no code fences, no prose outside JSON. Fields declared as arrays must be arrays; never return a string where an array or object array is required.
                """.formatted(outputContract());
    }

    static String outputContract() {
        return """
                {
                  "doctorSummary": "string",
                  "patientExplanation": "string",
                  "contextualInterpretation": "string",
                  "keyAbnormalItems": [
                    {"code": "string", "name": "string", "trend": "string", "interpretation": "string"}
                  ],
                  "contextLinks": [
                    {"type": "string", "symptoms": ["string"], "indicators": ["string"], "note": "string"}
                  ],
                  "riskNotes": ["string"],
                  "followUpQuestions": ["string"],
                  "suggestedDepartment": "string",
                  "suggestedAction": "string"
                }
                Use [] for empty keyAbnormalItems, contextLinks, riskNotes, or followUpQuestions.
                """;
    }

    public record CloudResult(CloudReportResponse response, String modelName, Integer promptTokens, Integer completionTokens, Integer totalTokens) {
    }

    public record RawCloudResult(String content, String modelName, Integer promptTokens, Integer completionTokens, Integer totalTokens) {
    }
}
