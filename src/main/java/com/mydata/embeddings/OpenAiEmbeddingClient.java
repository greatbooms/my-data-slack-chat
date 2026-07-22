package com.mydata.embeddings;

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

public class OpenAiEmbeddingClient implements EmbeddingClient {
    private static final String EMBEDDINGS_PATH = "/v1/embeddings";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI baseUri;
    private final String apiKey;
    private final String model;
    private final int dimensions;
    private final Duration requestTimeout;

    public OpenAiEmbeddingClient(
        HttpClient httpClient,
        ObjectMapper objectMapper,
        URI baseUri,
        String apiKey,
        String model,
        int dimensions,
        Duration requestTimeout
    ) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.baseUri = baseUri;
        this.apiKey = apiKey == null ? "" : apiKey;
        this.model = model;
        this.dimensions = dimensions;
        this.requestTimeout = requestTimeout;
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    @Override
    public float[] embed(String text) {
        if (apiKey.isBlank()) {
            throw new OpenAiEmbeddingException("OPENAI_API_KEY가 필요합니다");
        }

        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(EMBEDDINGS_PATH))
            .POST(HttpRequest.BodyPublishers.ofString(toJson(text), StandardCharsets.UTF_8))
            .timeout(requestTimeout)
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new OpenAiEmbeddingException("OpenAI 임베딩 API 요청에 실패했습니다", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new OpenAiEmbeddingException("OpenAI 임베딩 API 요청이 중단되었습니다", exception);
        }

        JsonNode root = readJson(response.body());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String code = blankToNull(root.path("error").path("code").asString(null));
            throw new OpenAiEmbeddingException(
                "OpenAI 임베딩 API 오류: status=" + response.statusCode()
                    + ", code=" + (code == null ? "unknown" : code)
            );
        }

        JsonNode embedding = root.path("data").path(0).path("embedding");
        if (!embedding.isArray()) {
            throw new OpenAiEmbeddingException("OpenAI 임베딩 API 응답에서 임베딩을 찾지 못했습니다");
        }

        float[] vector = new float[embedding.size()];
        for (int i = 0; i < embedding.size(); i++) {
            vector[i] = (float) embedding.get(i).asDouble();
        }
        if (vector.length != dimensions) {
            throw new OpenAiEmbeddingException(
                "OpenAI 임베딩 차원 불일치: 기대=" + dimensions + ", 실제=" + vector.length
            );
        }
        return vector;
    }

    private String toJson(String text) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("input", text == null ? "" : text);
            body.put("dimensions", dimensions);
            return objectMapper.writeValueAsString(body);
        } catch (Exception exception) {
            throw new OpenAiEmbeddingException("OpenAI 임베딩 API 요청 JSON을 생성하지 못했습니다", exception);
        }
    }

    private JsonNode readJson(String body) {
        try {
            return objectMapper.readTree(body == null ? "" : body);
        } catch (JacksonException exception) {
            throw new OpenAiEmbeddingException("OpenAI 임베딩 API 응답 JSON을 해석하지 못했습니다", exception);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
