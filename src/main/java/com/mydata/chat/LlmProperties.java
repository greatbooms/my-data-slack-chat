package com.mydata.chat;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "my-data.llm")
public record LlmProperties(
    String provider,
    String model,
    int maxInputChunks,
    int maxCharsPerChunk,
    int maxOutputTokens,
    Duration connectTimeout,
    Duration requestTimeout
) {
    private static final String DEFAULT_PROVIDER = "stub";
    private static final String DEFAULT_MODEL = "gpt-5.4-mini";
    private static final int DEFAULT_MAX_INPUT_CHUNKS = 5;
    private static final int DEFAULT_MAX_CHARS_PER_CHUNK = 1200;
    private static final int DEFAULT_MAX_OUTPUT_TOKENS = 700;
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);

    public LlmProperties {
        provider = blankToDefault(provider, DEFAULT_PROVIDER);
        model = blankToDefault(model, DEFAULT_MODEL);
        maxInputChunks = positiveOrDefault(maxInputChunks, DEFAULT_MAX_INPUT_CHUNKS);
        maxCharsPerChunk = positiveOrDefault(maxCharsPerChunk, DEFAULT_MAX_CHARS_PER_CHUNK);
        maxOutputTokens = positiveOrDefault(maxOutputTokens, DEFAULT_MAX_OUTPUT_TOKENS);
        connectTimeout = connectTimeout == null ? DEFAULT_CONNECT_TIMEOUT : connectTimeout;
        requestTimeout = requestTimeout == null ? DEFAULT_REQUEST_TIMEOUT : requestTimeout;
    }

    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static int positiveOrDefault(int value, int defaultValue) {
        return value > 0 ? value : defaultValue;
    }
}
