package com.liu.eemrsagent.agent;

import com.liu.eemrsagent.config.OllamaProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class PreConsultationService {

    private final OllamaProperties properties;
    private final RestClient restClient;

    public PreConsultationService(OllamaProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(properties.timeoutSeconds()));
        this.restClient = builder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    public PreConsultationResponse ask(PreConsultationRequest request) {
        String input = request.userInput();
        Map<String, Object> body = Map.of(
                "model", properties.model(),
                "stream", false,
                "messages", List.of(
                        Map.of(
                                "role", "system",
                                "content", "你是电子医疗系统中的用户预问诊助手。请用中文回答，先给出温和的初步建议，再列出需要补充的信息和就医提醒。不要做最终诊断，不要替代医生。"
                        ),
                        Map.of(
                                "role", "user",
                                "content", input
                        )
                )
        );

        OllamaChatResponse response;
        try {
            response = restClient.post()
                    .uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(OllamaChatResponse.class);
        } catch (RestClientException e) {
            throw new IllegalStateException("Ollama service is unavailable or model generation timed out", e);
        }

        String reply = response == null || response.message() == null
                ? ""
                : response.message().content();
        return new PreConsultationResponse(reply, properties.model());
    }

    private record OllamaChatResponse(OllamaMessage message) {
    }

    private record OllamaMessage(String role, String content) {
    }
}
