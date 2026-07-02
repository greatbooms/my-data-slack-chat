package com.mydata.chat;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

@ConfigurationProperties(prefix = "my-data.llm.claude")
public record ClaudeProperties(
    String apiKey,
    URI baseUrl,
    String apiVersion,
    String model
) {
    private static final URI DEFAULT_BASE_URL = URI.create("https://api.anthropic.com");
    private static final String DEFAULT_API_VERSION = "2023-06-01";
    private static final String DEFAULT_MODEL = "claude-sonnet-5";

    public ClaudeProperties {
        apiKey = apiKey == null ? "" : apiKey;
        baseUrl = baseUrl == null ? DEFAULT_BASE_URL : baseUrl;
        apiVersion = blankToDefault(apiVersion, DEFAULT_API_VERSION);
        model = blankToDefault(model, DEFAULT_MODEL);
    }

    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
