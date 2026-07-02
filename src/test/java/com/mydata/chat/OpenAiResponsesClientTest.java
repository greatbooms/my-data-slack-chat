package com.mydata.chat;

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

class OpenAiResponsesClientTest {
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
    void sendsResponsesRequestAndExtractsOutputText() {
        server.createContext("/v1/responses", exchange -> {
            requests.add(new RecordedRequest(
                exchange.getRequestMethod(),
                exchange.getRequestURI().toString(),
                exchange.getRequestHeaders().getFirst("Authorization"),
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)
            ));
            respond(exchange, 200, """
                {
                  "id": "resp_1",
                  "output": [
                    {
                      "type": "message",
                      "content": [
                        { "type": "output_text", "text": "근거 기반 답변입니다." }
                      ]
                    }
                  ]
                }
                """);
        });
        OpenAiResponsesClient client = client("api-key");

        String answer = client.createResponse(
            "gpt-test",
            new LlmPrompt("instructions", "input"),
            300
        );

        assertThat(answer).isEqualTo("근거 기반 답변입니다.");
        assertThat(requests)
            .singleElement()
            .satisfies(request -> {
                assertThat(request.method()).isEqualTo("POST");
                assertThat(request.path()).isEqualTo("/v1/responses");
                assertThat(request.authorization()).isEqualTo("Bearer api-key");
                JsonNode body = readJson(request.body());
                assertThat(body.path("model").asString()).isEqualTo("gpt-test");
                assertThat(body.path("instructions").asString()).isEqualTo("instructions");
                assertThat(body.path("input").asString()).isEqualTo("input");
                assertThat(body.path("max_output_tokens").asInt()).isEqualTo(300);
            });
    }

    @Test
    void throwsSanitizedExceptionForOpenAiErrors() {
        server.createContext("/v1/responses", exchange -> respond(exchange, 401, """
            {
              "error": {
                "message": "Incorrect API key provided: api-key",
                "code": "invalid_api_key"
              }
            }
            """));
        OpenAiResponsesClient client = client("api-key");

        assertThatThrownBy(() -> client.createResponse(
            "gpt-test",
            new LlmPrompt("instructions", "input"),
            300
        ))
            .isInstanceOf(OpenAiLlmException.class)
            .hasMessageContaining("401")
            .hasMessageContaining("invalid_api_key")
            .hasMessageNotContaining("api-key");
    }

    @Test
    void rejectsMissingApiKeyBeforeSendingRequest() {
        OpenAiResponsesClient client = client(" ");

        assertThatThrownBy(() -> client.createResponse(
            "gpt-test",
            new LlmPrompt("instructions", "input"),
            300
        ))
            .isInstanceOf(OpenAiLlmException.class)
            .hasMessageContaining("OPENAI_API_KEY");
        assertThat(requests).isEmpty();
    }

    private OpenAiResponsesClient client(String apiKey) {
        return new OpenAiResponsesClient(
            HttpClient.newHttpClient(),
            objectMapper,
            URI.create("http://localhost:" + server.getAddress().getPort()),
            apiKey,
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

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private record RecordedRequest(String method, String path, String authorization, String body) {
    }
}
