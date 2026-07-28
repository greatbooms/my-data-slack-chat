package com.mydata.connectors.googledrive;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

public class GoogleOAuthTokenProvider {
    private static final Duration EXPIRY_SAFETY_MARGIN = Duration.ofSeconds(60);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI tokenUrl;
    private final String clientId;
    private final String clientSecret;
    private final String refreshToken;
    private final Duration requestTimeout;
    private final Clock clock;

    private CachedToken cached;

    public GoogleOAuthTokenProvider(
        HttpClient httpClient,
        ObjectMapper objectMapper,
        URI tokenUrl,
        String clientId,
        String clientSecret,
        String refreshToken,
        Duration requestTimeout,
        Clock clock
    ) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.tokenUrl = tokenUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.refreshToken = refreshToken;
        this.requestTimeout = requestTimeout;
        this.clock = clock;
    }

    public synchronized String accessToken() {
        if (cached != null && clock.instant().isBefore(cached.expiresAt())) {
            return cached.value();
        }

        String body = "grant_type=refresh_token"
            + "&client_id=" + encode(clientId)
            + "&client_secret=" + encode(clientSecret)
            + "&refresh_token=" + encode(refreshToken);
        HttpRequest request = HttpRequest.newBuilder(tokenUrl)
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .timeout(requestTimeout)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept", "application/json")
            .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new GoogleDriveApiException("Google OAuth 토큰 갱신 요청에 실패했습니다", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new GoogleDriveApiException("Google OAuth 토큰 갱신이 중단되었습니다", exception);
        }
        if (response.statusCode() != 200) {
            throw new GoogleDriveApiException(
                "Google OAuth 토큰 갱신에 실패했습니다: " + response.body(), response.statusCode()
            );
        }

        JsonNode root = readJson(response.body());
        String accessToken = root.path("access_token").asString(null);
        long expiresInSeconds = root.path("expires_in").asLong(0);
        if (accessToken == null || accessToken.isBlank()) {
            throw new GoogleDriveApiException("Google OAuth 응답에 access_token이 없습니다", response.statusCode());
        }
        Instant expiresAt = clock.instant().plusSeconds(expiresInSeconds).minus(EXPIRY_SAFETY_MARGIN);
        cached = new CachedToken(accessToken, expiresAt);
        return accessToken;
    }

    private JsonNode readJson(String body) {
        try {
            return objectMapper.readTree(body == null ? "" : body);
        } catch (JacksonException exception) {
            throw new GoogleDriveApiException("Google OAuth 응답 JSON을 해석하지 못했습니다", exception);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record CachedToken(String value, Instant expiresAt) {
    }
}
