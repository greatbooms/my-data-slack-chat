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
            .containsEntry("my-data.llm.openai.base-url", "${OPENAI_BASE_URL:https://api.openai.com}");
        assertThat(properties)
            .doesNotContainKey("my-data.llm.api-key")
            .doesNotContainKey("my-data.llm.base-url");
    }

    @Test
    void openAiPropertiesHasOpenAiSpecificDefaults() {
        OpenAiProperties properties = new OpenAiProperties(null, null);

        assertThat(properties.apiKey()).isEmpty();
        assertThat(properties.baseUrl()).isEqualTo(URI.create("https://api.openai.com"));
    }
}
