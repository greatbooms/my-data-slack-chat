package com.mydata.connectors.notion;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotionApiClientTest {
    private HttpServer server;
    private final List<String> requests = new ArrayList<>();
    private final List<String> requestBodies = new ArrayList<>();

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
    void retrievePageSendsRequiredHeadersAndParsesMetadata() {
        server.createContext("/v1/pages/page-1", exchange -> {
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI()
                + " auth=" + exchange.getRequestHeaders().getFirst("Authorization")
                + " version=" + exchange.getRequestHeaders().getFirst("Notion-Version"));
            respond(exchange, 200, """
                {
                  "id": "page-1",
                  "url": "https://notion.so/page-1",
                  "public_url": "https://public.notion.site/page-1",
                  "created_time": "2026-06-01T00:00:00.000Z",
                  "last_edited_time": "2026-06-02T00:00:00.000Z",
                  "created_by": { "object": "user", "id": "creator-user" },
                  "last_edited_by": { "object": "user", "id": "editor-user" },
                  "parent": {
                    "type": "page_id",
                    "page_id": "parent-page"
                  },
                  "in_trash": true,
                  "properties": {
                    "Name": {
                      "type": "title",
                      "title": [
                        { "plain_text": "Project Brief" }
                      ]
                    },
                    "Status": {
                      "type": "status",
                      "status": { "name": "In progress" }
                    },
                    "Priority": {
                      "type": "select",
                      "select": { "name": "High" }
                    },
                    "Tags": {
                      "type": "multi_select",
                      "multi_select": [{ "name": "Backend" }, { "name": "RAG" }]
                    },
                    "Done": {
                      "type": "checkbox",
                      "checkbox": true
                    },
                    "Estimate": {
                      "type": "number",
                      "number": 8
                    },
                    "Due": {
                      "type": "date",
                      "date": { "start": "2026-07-13", "end": "2026-07-14" }
                    }
                  }
                }
                """);
        });
        NotionApiClient client = client();

        NotionApiClient.NotionPage page = client.retrievePage("page-1");

        assertThat(page.id()).isEqualTo("page-1");
        assertThat(page.title()).isEqualTo("Project Brief");
        assertThat(page.url()).isEqualTo("https://notion.so/page-1");
        assertThat(page.publicUrl()).isEqualTo("https://public.notion.site/page-1");
        assertThat(page.parentType()).isEqualTo("page_id");
        assertThat(page.parentId()).isEqualTo("parent-page");
        assertThat(page.inTrash()).isTrue();
        assertThat(page.createdByUserId()).isEqualTo("creator-user");
        assertThat(page.lastEditedByUserId()).isEqualTo("editor-user");
        assertThat(page.lastEditedTime().toString()).isEqualTo("2026-06-02T00:00:00Z");
        assertThat(page.properties()).containsExactly(
            org.assertj.core.data.MapEntry.entry("Status", "In progress"),
            org.assertj.core.data.MapEntry.entry("Priority", "High"),
            org.assertj.core.data.MapEntry.entry("Tags", "Backend, RAG"),
            org.assertj.core.data.MapEntry.entry("Done", "true"),
            org.assertj.core.data.MapEntry.entry("Estimate", "8"),
            org.assertj.core.data.MapEntry.entry("Due", "2026-07-13 - 2026-07-14")
        );
        assertThat(requests)
            .containsExactly("GET /v1/pages/page-1 auth=Bearer notion-token version=2026-03-11");
    }

    @Test
    void retrieveDatabaseSendsRequiredHeadersAndParsesDataSources() {
        server.createContext("/v1/databases/database-1", exchange -> {
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI()
                + " auth=" + exchange.getRequestHeaders().getFirst("Authorization")
                + " version=" + exchange.getRequestHeaders().getFirst("Notion-Version"));
            respond(exchange, 200, """
                {
                  "id": "database-1",
                  "url": "https://notion.so/database-1",
                  "title": [
                    { "plain_text": "Roadmap" }
                  ],
                  "data_sources": [
                    { "id": "data-source-1", "name": "Main view" }
                  ]
                }
                """);
        });
        NotionApiClient client = client();

        NotionApiClient.NotionDatabase database = client.retrieveDatabase("database-1");

        assertThat(database.id()).isEqualTo("database-1");
        assertThat(database.title()).isEqualTo("Roadmap");
        assertThat(database.url()).isEqualTo("https://notion.so/database-1");
        assertThat(database.dataSources()).singleElement()
            .satisfies(dataSource -> {
                assertThat(dataSource.id()).isEqualTo("data-source-1");
                assertThat(dataSource.name()).isEqualTo("Main view");
            });
        assertThat(requests)
            .containsExactly("GET /v1/databases/database-1 auth=Bearer notion-token version=2026-03-11");
    }

    @Test
    void queryDataSourcePagesPullsOneImmutableBatchWithExactCursorRequest() {
        server.createContext("/v1/data_sources/data-source-1/query", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requestBodies.add(body);
            if (!body.contains("start_cursor")) {
                respond(exchange, 200, pageQueryResponse(true, "cursor-2", "row-1", "First"));
                return;
            }
            respond(exchange, 200, pageQueryResponse(false, null, "row-2", "Second"));
        });

        NotionClient.Batch<NotionApiClient.NotionPage> first =
            client().queryDataSourcePages("data-source-1", null);

        assertThat(first.items()).extracting(NotionApiClient.NotionPage::id).containsExactly("row-1");
        assertThat(first.nextCursor()).isEqualTo("cursor-2");
        assertThat(requestBodies).containsExactly("{\"result_type\":\"page\",\"page_size\":100}");
        assertThatThrownBy(() -> first.items().add(first.items().getFirst()))
            .isInstanceOf(UnsupportedOperationException.class);

        NotionClient.Batch<NotionApiClient.NotionPage> second =
            client().queryDataSourcePages("data-source-1", first.nextCursor());

        assertThat(second.items()).extracting(NotionApiClient.NotionPage::id).containsExactly("row-2");
        assertThat(second.nextCursor()).isNull();
        assertThat(requestBodies).containsExactly(
            "{\"result_type\":\"page\",\"page_size\":100}",
            "{\"result_type\":\"page\",\"page_size\":100,\"start_cursor\":\"cursor-2\"}"
        );
    }

    @Test
    void queryDataSourcePagesKeepsDeliveredBatchWhenLaterCursorFails() {
        AtomicInteger requests = new AtomicInteger();
        server.createContext("/v1/data_sources/data-source-1/query", exchange -> {
            if (requests.getAndIncrement() == 0) {
                respond(exchange, 200, pageQueryResponse(true, "cursor-2", "row-1", "First"));
                return;
            }
            respond(exchange, 404, """
                {"object":"error","code":"object_not_found","message":"hidden"}
                """);
        });
        NotionClient.Batch<NotionApiClient.NotionPage> first =
            client().queryDataSourcePages("data-source-1", null);

        assertThatThrownBy(() -> client().queryDataSourcePages("data-source-1", first.nextCursor()))
            .isInstanceOf(NotionApiException.class)
            .satisfies(error -> {
                NotionApiException notionError = (NotionApiException) error;
                assertThat(notionError.statusCode()).isEqualTo(404);
                assertThat(notionError.code()).isEqualTo("object_not_found");
            });
        assertThat(first.items()).extracting(NotionApiClient.NotionPage::id).containsExactly("row-1");
    }

    @Test
    void queryDataSourcePagesThrowsWhenResultIsIncomplete() {
        server.createContext("/v1/data_sources/data-source-1/query", exchange -> respond(exchange, 200, """
            {
              "has_more": false,
              "next_cursor": null,
              "request_status": {
                "type": "incomplete",
                "incomplete_reason": "query_result_limit_reached"
              },
              "results": []
            }
            """));
        NotionApiClient client = client();

        assertThatThrownBy(() -> client.queryDataSourcePages("data-source-1", null))
            .isInstanceOf(NotionApiException.class)
            .hasMessageContaining("query_result_limit_reached")
            .hasMessageNotContaining("notion-token");
    }

    @Test
    void listBlockChildrenPullsOneImmutableBatchWithExactCursorRequest() {
        server.createContext("/v1/blocks/root/children", exchange -> {
            requests.add(exchange.getRequestURI().toString());
            String query = exchange.getRequestURI().getQuery();
            if (query == null || !query.contains("start_cursor")) {
                respond(exchange, 200, """
                    {
                      "has_more": true,
                      "next_cursor": "cursor-2",
                      "results": [
                        {
                          "id": "block-1",
                          "type": "paragraph",
                          "has_children": false,
                          "paragraph": { "rich_text": [ { "plain_text": "First" } ] }
                        }
                      ]
                    }
                    """);
                return;
            }
            respond(exchange, 200, """
                {
                  "has_more": false,
                  "next_cursor": null,
                  "results": [
                    {
                      "id": "block-2",
                      "type": "heading_1",
                      "has_children": true,
                      "heading_1": { "rich_text": [ { "plain_text": "Second" } ] }
                    }
                  ]
                }
                """);
        });
        NotionClient.Batch<NotionApiClient.NotionBlock> first = client().listBlockChildren("root", null);

        assertThat(first.items())
            .extracting(NotionApiClient.NotionBlock::id)
            .containsExactly("block-1");
        assertThat(first.nextCursor()).isEqualTo("cursor-2");
        assertThat(first.items().getFirst().plainText()).isEqualTo("First");
        assertThat(requests).containsExactly("/v1/blocks/root/children?page_size=100");
        assertThatThrownBy(() -> first.items().clear())
            .isInstanceOf(UnsupportedOperationException.class);

        NotionClient.Batch<NotionApiClient.NotionBlock> second =
            client().listBlockChildren("root", first.nextCursor());

        assertThat(second.items())
            .extracting(NotionApiClient.NotionBlock::id)
            .containsExactly("block-2");
        assertThat(second.nextCursor()).isNull();
        assertThat(second.items().getFirst().plainText()).isEqualTo("Second");
        assertThat(second.items().getFirst().hasChildren()).isTrue();
        assertThat(requests).containsExactly(
            "/v1/blocks/root/children?page_size=100",
            "/v1/blocks/root/children?page_size=100&start_cursor=cursor-2"
        );
    }

    @Test
    void listBlockChildrenDoesNotRequestNextCursorUntilCallerPullsIt() {
        AtomicInteger requests = new AtomicInteger();
        server.createContext("/v1/blocks/root/children", exchange -> {
            requests.incrementAndGet();
            respond(exchange, 200, """
                {
                  "has_more": true,
                  "next_cursor": "cursor-2",
                  "results": [
                    {
                      "id": "block-1",
                      "type": "paragraph",
                      "has_children": false,
                      "paragraph": { "rich_text": [ { "plain_text": "First" } ] }
                    }
                  ]
                }
                """);
        });

        NotionClient.Batch<NotionApiClient.NotionBlock> batch = client().listBlockChildren("root", null);

        assertThat(batch.nextCursor()).isEqualTo("cursor-2");
        assertThat(requests).hasValue(1);
    }

    @Test
    void listBlockChildrenParsesChildDatabaseAndUnsupportedUnderlyingType() {
        server.createContext("/v1/blocks/root/children", exchange -> respond(exchange, 200, """
            {
              "has_more": false,
              "next_cursor": null,
              "results": [
                {
                  "id": "database-1",
                  "type": "child_database",
                  "has_children": false,
                  "child_database": { "title": "Roadmap" }
                },
                {
                  "id": "unsupported-1",
                  "type": "unsupported",
                  "has_children": false,
                  "unsupported": { "block_type": "child_database" }
                }
              ]
            }
            """));
        List<NotionApiClient.NotionBlock> blocks = client().listBlockChildren("root", null).items();

        assertThat(blocks).hasSize(2);
        assertThat(blocks.get(0).type()).isEqualTo("child_database");
        assertThat(blocks.get(0).plainText()).isEqualTo("Roadmap");
        assertThat(blocks.get(0).underlyingType()).isNull();
        assertThat(blocks.get(1).type()).isEqualTo("unsupported");
        assertThat(blocks.get(1).plainText()).isEmpty();
        assertThat(blocks.get(1).underlyingType()).isEqualTo("child_database");
    }

    @Test
    void throwsSanitizedExceptionForNotionErrors() {
        server.createContext("/v1/pages/missing", exchange -> respond(exchange, 404, """
            { "object": "error", "code": "object_not_found", "message": "Could not find page" }
            """));
        NotionApiClient client = client();

        assertThatThrownBy(() -> client.retrievePage("missing"))
            .isInstanceOf(NotionApiException.class)
            .hasMessageContaining("404")
            .hasMessageContaining("object_not_found")
            .hasMessageNotContaining("notion-token");
    }

    @Test
    void timesOutSlowRequests() {
        server.createContext("/v1/pages/slow", exchange -> {
            try {
                Thread.sleep(500);
                respond(exchange, 200, """
                    { "id": "slow", "properties": {} }
                    """);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        });
        NotionApiClient client = new NotionApiClient(
            HttpClient.newHttpClient(),
            new ObjectMapper(),
            URI.create("http://localhost:" + server.getAddress().getPort()),
            "notion-token",
            "2026-03-11",
            Duration.ofMillis(50)
        );

        assertThatThrownBy(() -> client.retrievePage("slow"))
            .isInstanceOf(NotionApiException.class)
            .hasMessageContaining("Notion API 요청에 실패했습니다");
    }

    private String pageQueryResponse(
        boolean hasMore,
        String nextCursor,
        String pageId,
        String title
    ) {
        String cursorJson = nextCursor == null ? "null" : "\"" + nextCursor + "\"";
        return """
            {
              "has_more": %s,
              "next_cursor": %s,
              "request_status": { "type": "complete" },
              "results": [{
                "object": "page",
                "id": "%s",
                "url": "https://notion.so/%s",
                "created_time": "2026-07-13T00:00:00.000Z",
                "last_edited_time": "2026-07-13T00:00:00.000Z",
                "properties": {
                  "Name": {
                    "type": "title",
                    "title": [{ "plain_text": "%s" }]
                  }
                }
              }]
            }
            """.formatted(hasMore, cursorJson, pageId, pageId, title);
    }

    private NotionApiClient client() {
        return new NotionApiClient(
            HttpClient.newHttpClient(),
            new ObjectMapper(),
            URI.create("http://localhost:" + server.getAddress().getPort()),
            "notion-token",
            "2026-03-11"
        );
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
