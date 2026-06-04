package com.liu.eemrsagent.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class OllamaClient implements LlmClient {

    private final LlmProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public OllamaClient(LlmProperties properties, ObjectMapper objectMapper, RestClient.Builder builder) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(properties.getOllama().getTimeoutSeconds()));
        this.restClient = builder
                .baseUrl(properties.getOllama().getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public LlmChatResponse chat(LlmChatRequest request) {
        LlmProperties.Ollama ollama = properties.getOllama();
        if (!ollama.isEnabled()) {
            throw new LlmException("Ollama provider is disabled");
        }

        OllamaChatResponse response;
        String rawResponse;
        try {
            response = restClient.post()
                    .uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(buildBody(request, ollama))
                    .retrieve()
                    .body(OllamaChatResponse.class);
            rawResponse = safeWrite(response);
        } catch (ResourceAccessException e) {
            throw new LlmException("Ollama 响应超时，请稍后重试。", e);
        } catch (RestClientException e) {
            throw new LlmException("Ollama 服务不可用或模型生成失败。", e);
        }

        String content = response == null || response.message() == null ? "" : response.message().content();
        if (content == null || content.isBlank()) {
            throw new LlmException("Ollama 返回内容为空。");
        }
        return new LlmChatResponse(
                content.trim(),
                response.model() == null || response.model().isBlank() ? ollama.getModel() : response.model(),
                providerName(),
                response.promptEvalCount(),
                response.evalCount(),
                null,
                rawResponse
        );
    }

    @Override
    public String providerName() {
        return LlmProviderType.OLLAMA.value();
    }

    private Map<String, Object> buildBody(LlmChatRequest request, LlmProperties.Ollama ollama) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", ollama.getModel());
        body.put("stream", false);
        body.put("messages", request.messages());
        body.put("options", Map.of(
                "temperature", request.temperature() == null ? 0.2 : request.temperature(),
                "top_p", request.topP() == null ? 0.8 : request.topP()
        ));
        if (request.jsonModeEnabled()) {
            body.put("format", "json");
        }
        return body;
    }

    private String safeWrite(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "";
        }
    }

    private record OllamaChatResponse(
            String model,
            OllamaMessage message,
            @JsonProperty("prompt_eval_count") Integer promptEvalCount,
            @JsonProperty("eval_count") Integer evalCount
    ) {
    }

    private record OllamaMessage(String role, String content) {
    }
}
