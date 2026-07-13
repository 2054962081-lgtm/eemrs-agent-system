package com.liu.eemrsagent.trace;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TraceConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "agent.trace", name = "enabled", havingValue = "true", matchIfMissing = true)
    public AgentTraceRecorder enabledTraceRecorder(
            TraceRepository repository,
            TraceProperties properties,
            TracePayloads payloads,
            TraceRedactor redactor
    ) {
        return new EnabledTraceRecorder(repository, properties, payloads, redactor);
    }

    @Bean
    @ConditionalOnProperty(prefix = "agent.trace", name = "enabled", havingValue = "false")
    public AgentTraceRecorder noopTraceRecorder() {
        return new NoopTraceRecorder();
    }
}
