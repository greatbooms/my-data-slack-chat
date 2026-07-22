package com.mydata.embeddings;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;

@Configuration
@EnableConfigurationProperties({EmbeddingProperties.class, OpenAiEmbeddingProperties.class})
class EmbeddingConfiguration {
    // db/changelog의 document_embeddings.embedding vector(1536)와 일치해야 합니다.
    static final int PGVECTOR_DIMENSIONS = 1536;

    @Bean
    @ConditionalOnProperty(prefix = "my-data.embedding", name = "provider", havingValue = "openai")
    OpenAiEmbeddingClient openAiEmbeddingClient(
        EmbeddingProperties properties,
        OpenAiEmbeddingProperties openAiProperties,
        ObjectMapper objectMapper
    ) {
        if (properties.dimensions() != PGVECTOR_DIMENSIONS) {
            throw new IllegalStateException(
                "임베딩 차원이 pgvector 컬럼 차원과 일치하지 않습니다: 기대="
                    + PGVECTOR_DIMENSIONS + ", 실제=" + properties.dimensions()
            );
        }
        return new OpenAiEmbeddingClient(
            HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build(),
            objectMapper,
            openAiProperties.baseUrl(),
            openAiProperties.apiKey(),
            openAiProperties.model(),
            properties.dimensions(),
            properties.requestTimeout()
        );
    }
}
