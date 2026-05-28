package com.liu.eemrsagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent.ollama")
public record OllamaProperties(
        String baseUrl,
        String model,
        int timeoutSeconds
) {
}
