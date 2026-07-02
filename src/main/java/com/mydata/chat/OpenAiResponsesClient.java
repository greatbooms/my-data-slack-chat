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
import java.util.Map;

public class OpenAiResponsesClient {
    private static final String RESPONSES_PATH = "/v1/responses";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI baseUri;
    private final String apiKey;
    private final Duration requestTimeout;

    public OpenAiResponsesClient(
        HttpClient httpClient,
        ObjectMapper objectMapper,
        URI baseUri,
        String apiKey,
        Duration requestTimeout
    ) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.baseUri = baseUri;
        this.apiKey = apiKey == null ? "" : apiKey;
        this.requestTimeout = requestTimeout;
    }

    public String createResponse(String model, LlmPrompt prompt, int maxOutputTokens) {
        if (apiKey.isBlank()) {
            throw new OpenAiLlmException("OPENAI_API_KEY가 필요합니다");
        }

        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(RESPONSES_PATH))
            .POST(HttpRequest.BodyPublishers.ofString(toJson(model, prompt, maxOutputTokens), StandardCharsets.UTF_8))
            .timeout(requestTimeout)
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new OpenAiLlmException("OpenAI API 요청에 실패했습니다", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new OpenAiLlmException("OpenAI API 요청이 중단되었습니다", exception);
        }

        JsonNode root = readJson(response.body());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String code = blankToNull(root.path("error").path("code").asString(null));
            throw new OpenAiLlmException(
                "OpenAI API 오류: status=" + response.statusCode() + ", code=" + (code == null ? "unknown" : code)
            );
        }

        String text = extractOutputText(root);
        if (text == null || text.isBlank()) {
            throw new OpenAiLlmException("OpenAI API 응답에서 답변 텍스트를 찾지 못했습니다");
        }
        return text.trim();
    }

    private String toJson(String model, LlmPrompt prompt, int maxOutputTokens) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("instructions", prompt.instructions());
            body.put("input", prompt.input());
            body.put("max_output_tokens", maxOutputTokens);
            return objectMapper.writeValueAsString(body);
        } catch (Exception exception) {
            throw new OpenAiLlmException("OpenAI API 요청 JSON을 생성하지 못했습니다", exception);
        }
    }

    private JsonNode readJson(String body) {
        try {
            return objectMapper.readTree(body == null ? "" : body);
        } catch (JacksonException exception) {
            throw new OpenAiLlmException("OpenAI API 응답 JSON을 해석하지 못했습니다", exception);
        }
    }

    private String extractOutputText(JsonNode root) {
        String directOutputText = blankToNull(root.path("output_text").asString(null));
        if (directOutputText != null) {
            return directOutputText;
        }

        StringBuilder builder = new StringBuilder();
        JsonNode output = root.path("output");
        if (!output.isArray()) {
            return null;
        }
        for (JsonNode item : output) {
            JsonNode content = item.path("content");
            if (!content.isArray()) {
                continue;
            }
            for (JsonNode contentItem : content) {
                if ("output_text".equals(contentItem.path("type").asString())) {
                    String text = blankToNull(contentItem.path("text").asString(null));
                    if (text != null) {
                        if (!builder.isEmpty()) {
                            builder.append("\n");
                        }
                        builder.append(text);
                    }
                }
            }
        }
        return builder.isEmpty() ? null : builder.toString();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
