package com.mydata.chat;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

@ConfigurationProperties(prefix = "my-data.llm.openai")
public record OpenAiProperties(
    String apiKey,
    URI baseUrl,
    String model
) {
    private static final URI DEFAULT_BASE_URL = URI.create("https://api.openai.com");
    private static final String DEFAULT_MODEL = "gpt-5.6-terra";

    public OpenAiProperties {
        apiKey = apiKey == null ? "" : apiKey;
        baseUrl = baseUrl == null ? DEFAULT_BASE_URL : baseUrl;
        model = blankToDefault(model, DEFAULT_MODEL);
    }

    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
