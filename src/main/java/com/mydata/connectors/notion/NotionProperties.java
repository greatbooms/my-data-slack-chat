package com.mydata.connectors.notion;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties(prefix = "my-data.notion")
public record NotionProperties(
    String apiToken,
    String apiVersion,
    URI baseUrl,
    Duration connectTimeout,
    Duration requestTimeout
) {
    private static final String DEFAULT_API_VERSION = "2026-03-11";
    private static final URI DEFAULT_BASE_URL = URI.create("https://api.notion.com");
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);

    public NotionProperties {
        apiToken = apiToken == null ? "" : apiToken;
        apiVersion = apiVersion == null || apiVersion.isBlank() ? DEFAULT_API_VERSION : apiVersion;
        baseUrl = baseUrl == null ? DEFAULT_BASE_URL : baseUrl;
        connectTimeout = connectTimeout == null ? DEFAULT_CONNECT_TIMEOUT : connectTimeout;
        requestTimeout = requestTimeout == null ? DEFAULT_REQUEST_TIMEOUT : requestTimeout;
    }
}
