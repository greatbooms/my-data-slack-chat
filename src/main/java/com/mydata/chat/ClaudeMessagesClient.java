package com.mydata.chat;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ClaudeMessagesClient {
    private static final String MESSAGES_PATH = "/v1/messages";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI baseUri;
    private final String apiKey;
    private final String apiVersion;
    private final Duration requestTimeout;

    public ClaudeMessagesClient(
        HttpClient httpClient,
        ObjectMapper objectMapper,
        URI baseUri,
        String apiKey,
        String apiVersion,
        Duration requestTimeout
    ) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.baseUri = baseUri;
        this.apiKey = apiKey == null ? "" : apiKey;
        this.apiVersion = apiVersion == null ? "" : apiVersion;
        this.requestTimeout = requestTimeout;
    }

    public String createMessage(String model, LlmPrompt prompt, int maxOutputTokens) {
        if (apiKey.isBlank()) {
            throw new ClaudeLlmException("CLAUDE_API_KEY가 필요합니다");
        }

        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(MESSAGES_PATH))
            .POST(HttpRequest.BodyPublishers.ofString(toJson(model, prompt, maxOutputTokens), StandardCharsets.UTF_8))
            .timeout(requestTimeout)
            .header("x-api-key", apiKey)
            .header("anthropic-version", apiVersion)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new ClaudeLlmException("Claude API 요청에 실패했습니다", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ClaudeLlmException("Claude API 요청이 중단되었습니다", exception);
        }

        JsonNode root = readJson(response.body(), response.statusCode());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String type = errorField(root, "type");
            String message = sanitizeErrorMessage(errorField(root, "message"));
            throw new ClaudeLlmException(
                "Claude API 오류: status=" + response.statusCode() + ", type=" + (type == null ? "unknown" : type)
                    + (message == null ? "" : ", message=" + message)
            );
        }
        if (root == null) {
            throw new ClaudeLlmException("Claude API 응답 JSON을 해석하지 못했습니다");
        }

        String text = extractText(root);
        if (text == null || text.isBlank()) {
            throw new ClaudeLlmException("Claude API 응답에서 답변 텍스트를 찾지 못했습니다");
        }
        return text.trim();
    }

    private String toJson(String model, LlmPrompt prompt, int maxOutputTokens) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("max_tokens", maxOutputTokens);
            body.put("system", prompt.instructions());
            body.put("messages", List.of(Map.of(
                "role", "user",
                "content", prompt.input()
            )));
            return objectMapper.writeValueAsString(body);
        } catch (Exception exception) {
            throw new ClaudeLlmException("Claude API 요청 JSON을 생성하지 못했습니다", exception);
        }
    }

    private JsonNode readJson(String body, int statusCode) {
        try {
            return objectMapper.readTree(body == null ? "" : body);
        } catch (JacksonException exception) {
            if (statusCode < 200 || statusCode >= 300) {
                return null;
            }
            throw new ClaudeLlmException("Claude API 응답 JSON을 해석하지 못했습니다", exception);
        }
    }

    private String errorField(JsonNode root, String fieldName) {
        if (root == null) {
            return null;
        }
        return blankToNull(root.path("error").path(fieldName).asString(null));
    }

    private String sanitizeErrorMessage(String message) {
        String sanitized = blankToNull(message);
        if (sanitized == null) {
            return null;
        }
        if (!apiKey.isBlank()) {
            sanitized = sanitized.replace(apiKey, "[REDACTED]");
        }
        if (sanitized.length() <= 240) {
            return sanitized;
        }
        return sanitized.substring(0, 240) + "...";
    }

    private String extractText(JsonNode root) {
        JsonNode content = root.path("content");
        if (!content.isArray()) {
            return null;
        }

        StringBuilder builder = new StringBuilder();
        for (JsonNode contentItem : content) {
            if ("text".equals(contentItem.path("type").asString())) {
                String text = blankToNull(contentItem.path("text").asString(null));
                if (text != null) {
                    if (!builder.isEmpty()) {
                        builder.append("\n");
                    }
                    builder.append(text);
                }
            }
        }
        return builder.isEmpty() ? null : builder.toString();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
