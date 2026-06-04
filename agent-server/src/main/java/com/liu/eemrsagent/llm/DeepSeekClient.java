package com.liu.eemrsagent.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DeepSeekClient implements LlmClient {

    private final LlmProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public DeepSeekClient(LlmProperties properties, ObjectMapper objectMapper, RestClient.Builder builder) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(properties.getDeepseek().getTimeoutSeconds()));
        this.restClient = builder
                .baseUrl(properties.getDeepseek().getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public LlmChatResponse chat(LlmChatRequest request) {
        LlmProperties.DeepSeek deepseek = properties.getDeepseek();
        if (!deepseek.isEnabled()) {
            throw new LlmException("DeepSeek provider is disabled");
        }
        if (deepseek.getApiKey() == null || deepseek.getApiKey().isBlank()) {
            throw new LlmException("DeepSeek API key 未配置，请设置环境变量 DEEPSEEK_API_KEY。");
        }

        DeepSeekChatResponse response;
        String rawResponse;
        try {
            response = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + deepseek.getApiKey())
                    .body(buildBody(request, deepseek))
                    .retrieve()
                    .body(DeepSeekChatResponse.class);
            rawResponse = safeWrite(response);
        } catch (HttpStatusCodeException e) {
            throw mapHttpError(e);
        } catch (ResourceAccessException e) {
            throw new LlmException("DeepSeek 响应超时，请稍后重试。", e);
        } catch (RestClientException e) {
            throw new LlmException("DeepSeek 服务暂时不可用，请稍后重试。", e);
        }

        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new LlmException("DeepSeek 返回内容为空。");
        }
        DeepSeekChoice choice = response.choices().get(0);
        String content = choice == null || choice.message() == null ? "" : choice.message().content();
        if (content == null || content.isBlank()) {
            throw new LlmException("DeepSeek 返回内容为空。");
        }
        DeepSeekUsage usage = response.usage();
        return new LlmChatResponse(
                content.trim(),
                response.model() == null || response.model().isBlank() ? deepseek.getModel() : response.model(),
                providerName(),
                usage == null ? null : usage.promptTokens(),
                usage == null ? null : usage.completionTokens(),
                usage == null ? null : usage.totalTokens(),
                rawResponse
        );
    }

    @Override
    public String providerName() {
        return LlmProviderType.DEEPSEEK.value();
    }

    private Map<String, Object> buildBody(LlmChatRequest request, LlmProperties.DeepSeek deepseek) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", deepseek.getModel());
        body.put("messages", request.messages());
        body.put("stream", false);
        body.put("temperature", request.temperature() == null ? deepseek.getTemperature() : request.temperature());
        body.put("top_p", request.topP() == null ? deepseek.getTopP() : request.topP());
        body.put("max_tokens", request.maxTokens() == null ? deepseek.getMaxTokens() : request.maxTokens());
        if (request.jsonModeEnabled()) {
            body.put("response_format", Map.of("type", "json_object"));
        }
        return body;
    }

    private LlmException mapHttpError(HttpStatusCodeException e) {
        int status = e.getStatusCode().value();
        if (status == 401) {
            return new LlmException("DeepSeek API key 无效或未授权。", e);
        }
        if (status == 429) {
            return new LlmException("DeepSeek 请求频率过高或额度不足，请稍后重试。", e);
        }
        if (status >= 500) {
            return new LlmException("DeepSeek 服务暂时不可用，请稍后重试。", e);
        }
        return new LlmException("DeepSeek 调用失败，请稍后重试。", e);
    }

    private String safeWrite(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "";
        }
    }

    public record DeepSeekChatResponse(
            String id,
            String object,
            Long created,
            String model,
            List<DeepSeekChoice> choices,
            DeepSeekUsage usage
    ) {
    }

    public record DeepSeekChoice(Integer index, DeepSeekMessage message, @JsonProperty("finish_reason") String finishReason) {
    }

    public record DeepSeekMessage(String role, String content) {
    }

    public record DeepSeekUsage(
            @JsonProperty("prompt_tokens") Integer promptTokens,
            @JsonProperty("completion_tokens") Integer completionTokens,
            @JsonProperty("total_tokens") Integer totalTokens
    ) {
    }
}
