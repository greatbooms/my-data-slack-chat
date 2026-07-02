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

class ClaudeMessagesClientTest {
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
    void sendsMessagesRequestAndExtractsTextContent() {
        server.createContext("/v1/messages", exchange -> {
            requests.add(new RecordedRequest(
                exchange.getRequestMethod(),
                exchange.getRequestURI().toString(),
                exchange.getRequestHeaders().getFirst("x-api-key"),
                exchange.getRequestHeaders().getFirst("anthropic-version"),
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)
            ));
            respond(exchange, 200, """
                {
                  "id": "msg_1",
                  "type": "message",
                  "role": "assistant",
                  "content": [
                    { "type": "text", "text": "첫 번째 답변" },
                    { "type": "text", "text": "두 번째 답변" }
                  ]
                }
                """);
        });
        ClaudeMessagesClient client = client("claude-key");

        String answer = client.createMessage(
            "claude-test",
            new LlmPrompt("system instructions", "user input"),
            300
        );

        assertThat(answer).isEqualTo("""
            첫 번째 답변
            두 번째 답변
            """.stripTrailing());
        assertThat(requests)
            .singleElement()
            .satisfies(request -> {
                assertThat(request.method()).isEqualTo("POST");
                assertThat(request.path()).isEqualTo("/v1/messages");
                assertThat(request.apiKey()).isEqualTo("claude-key");
                assertThat(request.apiVersion()).isEqualTo("2023-06-01");
                JsonNode body = readJson(request.body());
                assertThat(body.path("model").asString()).isEqualTo("claude-test");
                assertThat(body.path("max_tokens").asInt()).isEqualTo(300);
                assertThat(body.path("system").asString()).isEqualTo("system instructions");
                assertThat(body.path("messages").get(0).path("role").asString()).isEqualTo("user");
                assertThat(body.path("messages").get(0).path("content").asString()).isEqualTo("user input");
            });
    }

    @Test
    void throwsSanitizedExceptionForClaudeErrors() {
        server.createContext("/v1/messages", exchange -> respond(exchange, 401, """
            {
              "type": "error",
              "error": {
                "type": "authentication_error",
                "message": "invalid x-api-key: claude-key"
              }
            }
            """));
        ClaudeMessagesClient client = client("claude-key");

        assertThatThrownBy(() -> client.createMessage(
            "claude-test",
            new LlmPrompt("instructions", "input"),
            300
        ))
            .isInstanceOf(ClaudeLlmException.class)
            .hasMessageContaining("401")
            .hasMessageContaining("authentication_error")
            .hasMessageContaining("invalid x-api-key: [REDACTED]")
            .hasMessageNotContaining("claude-key");
    }

    @Test
    void reportsHttpStatusWhenClaudeErrorBodyIsNotJson() {
        server.createContext("/v1/messages", exchange -> respond(exchange, 503, "temporarily unavailable"));
        ClaudeMessagesClient client = client("claude-key");

        assertThatThrownBy(() -> client.createMessage(
            "claude-test",
            new LlmPrompt("instructions", "input"),
            300
        ))
            .isInstanceOf(ClaudeLlmException.class)
            .hasMessageContaining("503")
            .hasMessageContaining("unknown")
            .hasMessageNotContaining("temporarily unavailable")
            .hasMessageNotContaining("claude-key");
    }

    @Test
    void rejectsMissingApiKeyBeforeSendingRequest() {
        ClaudeMessagesClient client = client(" ");

        assertThatThrownBy(() -> client.createMessage(
            "claude-test",
            new LlmPrompt("instructions", "input"),
            300
        ))
            .isInstanceOf(ClaudeLlmException.class)
            .hasMessageContaining("CLAUDE_API_KEY");
        assertThat(requests).isEmpty();
    }

    private ClaudeMessagesClient client(String apiKey) {
        return new ClaudeMessagesClient(
            HttpClient.newHttpClient(),
            objectMapper,
            URI.create("http://localhost:" + server.getAddress().getPort()),
            apiKey,
            "2023-06-01",
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

    private record RecordedRequest(String method, String path, String apiKey, String apiVersion, String body) {
    }
}
