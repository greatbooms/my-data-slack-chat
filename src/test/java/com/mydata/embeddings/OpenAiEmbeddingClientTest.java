package com.mydata.embeddings;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiEmbeddingClientTest {
    private HttpServer server;
    private final List<RecordedRequest> requests = new ArrayList<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsEmbeddingRequestAndParsesVector() {
        server.createContext("/v1/embeddings", exchange -> {
            requests.add(new RecordedRequest(
                exchange.getRequestMethod(),
                exchange.getRequestURI().toString(),
                exchange.getRequestHeaders().getFirst("Authorization"),
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)
            ));
            respond(exchange, 200, """
                {
                  "data": [
                    { "embedding": [0.1, 0.2, 0.3] }
                  ]
                }
                """);
        });
        OpenAiEmbeddingClient client = client("api-key", 3);

        float[] embedding = client.embed("검색할 문장");

        assertThat(embedding).containsExactly(0.1f, 0.2f, 0.3f);
        assertThat(client.model()).isEqualTo("text-embedding-test");
        assertThat(client.dimensions()).isEqualTo(3);
        assertThat(requests)
            .singleElement()
            .satisfies(request -> {
                assertThat(request.method()).isEqualTo("POST");
                assertThat(request.path()).isEqualTo("/v1/embeddings");
                assertThat(request.authorization()).isEqualTo("Bearer api-key");
                JsonNode body = readJson(request.body());
                assertThat(body.path("model").asString()).isEqualTo("text-embedding-test");
                assertThat(body.path("input").asString()).isEqualTo("검색할 문장");
                assertThat(body.path("dimensions").asInt()).isEqualTo(3);
            });
    }

    @Test
    void throwsSanitizedExceptionForOpenAiErrors() {
        server.createContext("/v1/embeddings", exchange -> respond(exchange, 401, """
            {
              "error": {
                "message": "Incorrect API key provided: secret-api-key",
                "code": "invalid_api_key"
              }
            }
            """));
        OpenAiEmbeddingClient client = client("secret-api-key", 3);

        assertThatThrownBy(() -> client.embed("검색할 문장"))
            .isInstanceOf(OpenAiEmbeddingException.class)
            .hasMessageContaining("401")
            .hasMessageContaining("invalid_api_key")
            .hasMessageNotContaining("secret-api-key");
    }

    @Test
    void rejectsMissingApiKeyBeforeSendingRequest() {
        OpenAiEmbeddingClient client = client(" ", 3);

        assertThatThrownBy(() -> client.embed("검색할 문장"))
            .isInstanceOf(OpenAiEmbeddingException.class)
            .hasMessageContaining("OPENAI_API_KEY");
        assertThat(requests).isEmpty();
    }

    @Test
    void rejectsUnexpectedEmbeddingDimensions() {
        server.createContext("/v1/embeddings", exchange -> respond(exchange, 200, """
            {
              "data": [
                { "embedding": [0.1, 0.2] }
              ]
            }
            """));
        OpenAiEmbeddingClient client = client("api-key", 3);

        assertThatThrownBy(() -> client.embed("검색할 문장"))
            .isInstanceOf(OpenAiEmbeddingException.class)
            .hasMessageContaining("차원 불일치")
            .hasMessageContaining("기대=3")
            .hasMessageContaining("실제=2");
    }

    private OpenAiEmbeddingClient client(String apiKey, int dimensions) {
        return new OpenAiEmbeddingClient(
            HttpClient.newHttpClient(),
            objectMapper,
            URI.create("http://localhost:" + server.getAddress().getPort()),
            apiKey,
            "text-embedding-test",
            dimensions,
            Duration.ofSeconds(5)
        );
    }

    private JsonNode readJson(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
        throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private record RecordedRequest(String method, String path, String authorization, String body) {
    }
}
