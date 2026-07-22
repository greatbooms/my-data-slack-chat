package com.mydata.embeddings;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "my-data.embedding")
public record EmbeddingProperties(
    String provider,
    int dimensions,
    Duration connectTimeout,
    Duration requestTimeout
) {
    private static final String DEFAULT_PROVIDER = "deterministic";
    private static final int DEFAULT_DIMENSIONS = 1536;
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);

    public EmbeddingProperties {
        provider = blankToDefault(provider, DEFAULT_PROVIDER);
        dimensions = positiveOrDefault(dimensions, DEFAULT_DIMENSIONS);
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
