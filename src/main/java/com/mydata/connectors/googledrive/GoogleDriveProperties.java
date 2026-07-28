package com.mydata.connectors.googledrive;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties(prefix = "my-data.google-drive")
public record GoogleDriveProperties(
    String clientId,
    String clientSecret,
    String refreshToken,
    URI tokenUrl,
    URI apiBaseUrl,
    Duration connectTimeout,
    Duration requestTimeout
) {
    private static final URI DEFAULT_TOKEN_URL = URI.create("https://oauth2.googleapis.com/token");
    private static final URI DEFAULT_API_BASE_URL = URI.create("https://www.googleapis.com");
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);

    public GoogleDriveProperties {
        clientId = clientId == null ? "" : clientId;
        clientSecret = clientSecret == null ? "" : clientSecret;
        refreshToken = refreshToken == null ? "" : refreshToken;
        tokenUrl = tokenUrl == null ? DEFAULT_TOKEN_URL : tokenUrl;
        apiBaseUrl = apiBaseUrl == null ? DEFAULT_API_BASE_URL : apiBaseUrl;
        connectTimeout = connectTimeout == null ? DEFAULT_CONNECT_TIMEOUT : connectTimeout;
        requestTimeout = requestTimeout == null ? DEFAULT_REQUEST_TIMEOUT : requestTimeout;
    }
}
