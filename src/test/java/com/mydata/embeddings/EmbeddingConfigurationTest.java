package com.mydata.embeddings;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withBean(ObjectMapper.class, ObjectMapper::new)
        .withUserConfiguration(EmbeddingConfiguration.class, DeterministicEmbeddingClient.class);

    @Test
    void usesDeterministicClientWhenProviderIsMissing() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(EmbeddingClient.class);
            assertThat(context.getBean(EmbeddingClient.class)).isInstanceOf(DeterministicEmbeddingClient.class);
        });
    }

    @Test
    void usesDeterministicClientWhenProviderIsDeterministic() {
        contextRunner
            .withPropertyValues("my-data.embedding.provider=deterministic")
            .run(context -> {
                assertThat(context).hasSingleBean(EmbeddingClient.class);
                assertThat(context.getBean(EmbeddingClient.class)).isInstanceOf(DeterministicEmbeddingClient.class);
            });
    }

    @Test
    void usesOpenAiClientWhenProviderIsOpenAi() {
        contextRunner
            .withPropertyValues("my-data.embedding.provider=openai")
            .run(context -> {
                assertThat(context).hasSingleBean(EmbeddingClient.class);
                assertThat(context.getBean(EmbeddingClient.class)).isInstanceOf(OpenAiEmbeddingClient.class);
            });
    }

    @Test
    void rejectsDimensionsThatDoNotMatchPgvectorColumn() {
        contextRunner
            .withPropertyValues(
                "my-data.embedding.provider=openai",
                "my-data.embedding.dimensions=512"
            )
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalStateException.class)
                    .hasRootCauseMessage("임베딩 차원이 pgvector 컬럼 차원과 일치하지 않습니다: 기대=1536, 실제=512");
            });
    }

    @Test
    void embeddingPropertiesApplyDefaults() {
        EmbeddingProperties properties = new EmbeddingProperties(null, 0, null, null);

        assertThat(properties.provider()).isEqualTo("deterministic");
        assertThat(properties.dimensions()).isEqualTo(1536);
        assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.requestTimeout()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void openAiEmbeddingPropertiesApplyDefaults() {
        OpenAiEmbeddingProperties properties = new OpenAiEmbeddingProperties(null, null, null);

        assertThat(properties.apiKey()).isEmpty();
        assertThat(properties.baseUrl()).isEqualTo(URI.create("https://api.openai.com"));
        assertThat(properties.model()).isEqualTo("text-embedding-3-small");
    }
}
