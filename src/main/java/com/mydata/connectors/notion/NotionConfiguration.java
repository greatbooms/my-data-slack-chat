package com.mydata.connectors.notion;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;

@Configuration
@EnableConfigurationProperties(NotionProperties.class)
class NotionConfiguration {
    @Bean
    NotionClient notionClient(NotionProperties properties, ObjectMapper objectMapper) {
        return new NotionApiClient(
            HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build(),
            objectMapper,
            properties.baseUrl(),
            properties.apiToken(),
            properties.apiVersion(),
            properties.requestTimeout()
        );
    }
}
