package com.mydata.chat;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

@ConfigurationProperties(prefix = "my-data.llm.openai")
public record OpenAiProperties(
    String apiKey,
    URI baseUrl
) {
    private static final URI DEFAULT_BASE_URL = URI.create("https://api.openai.com");

    public OpenAiProperties {
        apiKey = apiKey == null ? "" : apiKey;
        baseUrl = baseUrl == null ? DEFAULT_BASE_URL : baseUrl;
    }
}
