package com.mydata.chat;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;

@Configuration
@EnableConfigurationProperties({LlmProperties.class, OpenAiProperties.class})
class LlmConfiguration {
    @Bean
    LlmPromptBuilder llmPromptBuilder(LlmProperties properties) {
        return new LlmPromptBuilder(properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "my-data.llm", name = "provider", havingValue = "openai")
    OpenAiResponsesClient openAiResponsesClient(
        LlmProperties properties,
        OpenAiProperties openAiProperties,
        ObjectMapper objectMapper
    ) {
        return new OpenAiResponsesClient(
            HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build(),
            objectMapper,
            openAiProperties.baseUrl(),
            openAiProperties.apiKey(),
            properties.requestTimeout()
        );
    }
}
