package com.mydata.chat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.net.URI;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class LlmConfigurationPropertiesTest {
    @Test
    void applicationYamlKeepsOpenAiSettingsUnderProviderNamespace() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));

        Properties properties = yaml.getObject();

        assertThat(properties)
            .isNotNull()
            .containsEntry("my-data.llm.provider", "${MY_DATA_LLM_PROVIDER:stub}")
            .containsEntry("my-data.llm.openai.api-key", "${OPENAI_API_KEY:}")
            .containsEntry("my-data.llm.openai.base-url", "${OPENAI_BASE_URL:https://api.openai.com}")
            .containsEntry("my-data.llm.openai.model", "${OPENAI_MODEL:${MY_DATA_LLM_MODEL:gpt-5.6-terra}}");
        assertThat(properties)
            .doesNotContainKey("my-data.llm.model")
            .doesNotContainKey("my-data.llm.api-key")
            .doesNotContainKey("my-data.llm.base-url");
    }

    @Test
    void applicationYamlKeepsClaudeSettingsUnderProviderNamespace() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));

        Properties properties = yaml.getObject();

        assertThat(properties)
            .isNotNull()
            .containsEntry("my-data.llm.claude.api-key", "${CLAUDE_API_KEY:}")
            .containsEntry("my-data.llm.claude.base-url", "${CLAUDE_BASE_URL:https://api.anthropic.com}")
            .containsEntry("my-data.llm.claude.api-version", "${CLAUDE_API_VERSION:2023-06-01}")
            .containsEntry("my-data.llm.claude.model", "${CLAUDE_MODEL:claude-sonnet-5}");
    }

    @Test
    void openAiPropertiesHasOpenAiSpecificDefaults() {
        OpenAiProperties properties = new OpenAiProperties(null, null, null);

        assertThat(properties.apiKey()).isEmpty();
        assertThat(properties.baseUrl()).isEqualTo(URI.create("https://api.openai.com"));
        assertThat(properties.model()).isEqualTo("gpt-5.6-terra");
    }

    @Test
    void claudePropertiesHasClaudeSpecificDefaults() {
        ClaudeProperties properties = new ClaudeProperties(null, null, null, null);

        assertThat(properties.apiKey()).isEmpty();
        assertThat(properties.baseUrl()).isEqualTo(URI.create("https://api.anthropic.com"));
        assertThat(properties.apiVersion()).isEqualTo("2023-06-01");
        assertThat(properties.model()).isEqualTo("claude-sonnet-5");
    }
}
