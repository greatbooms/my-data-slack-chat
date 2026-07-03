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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotionApiClientTest {
    private HttpServer server;
    private final List<String> requests = new ArrayList<>();

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
    void queryDataSourcePagesPostsPageFilterAndFollowsPagination() {
        server.createContext("/v1/data_sources/data-source-1/query", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI() + " body=" + body);
            if (!body.contains("start_cursor")) {
                respond(exchange, 200, """
                    {
                      "has_more": true,
                      "next_cursor": "cursor-2",
                      "request_status": { "type": "complete" },
                      "results": [
                        {
                          "object": "page",
                          "id": "row-1",
                          "url": "https://notion.so/row-1",
                          "created_time": "2026-06-01T00:00:00.000Z",
                          "last_edited_time": "2026-06-03T00:00:00.000Z",
                          "properties": {
                            "Name": {
                              "type": "title",
                              "title": [ { "plain_text": "First task" } ]
                            },
                            "Status": {
                              "type": "select",
                              "select": { "name": "Done" }
                            }
                          }
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
                  "request_status": { "type": "complete" },
                  "results": [
                    {
                      "object": "page",
                      "id": "row-2",
                      "url": "https://notion.so/row-2",
                      "created_time": "2026-06-02T00:00:00.000Z",
                      "last_edited_time": "2026-06-04T00:00:00.000Z",
                      "properties": {
                        "Name": {
                          "type": "title",
                          "title": [ { "plain_text": "Second task" } ]
                        },
                        "Done": {
                          "type": "checkbox",
                          "checkbox": true
                        }
                      }
                    }
                  ]
                }
                """);
        });
        NotionApiClient client = client();

        List<NotionApiClient.NotionPage> pages = client.queryDataSourcePages("data-source-1");

        assertThat(pages)
            .extracting(NotionApiClient.NotionPage::id)
            .containsExactly("row-1", "row-2");
        assertThat(pages.get(0).title()).isEqualTo("First task");
        assertThat(pages.get(0).properties()).containsEntry("Status", "Done");
        assertThat(pages.get(1).properties()).containsEntry("Done", "true");
        assertThat(requests).hasSize(2);
        assertThat(requests.get(0))
            .contains("POST /v1/data_sources/data-source-1/query")
            .contains("\"result_type\":\"page\"")
            .contains("\"page_size\":100")
            .doesNotContain("start_cursor");
        assertThat(requests.get(1))
            .contains("\"start_cursor\":\"cursor-2\"");
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

        assertThatThrownBy(() -> client.queryDataSourcePages("data-source-1"))
            .isInstanceOf(NotionApiException.class)
            .hasMessageContaining("query_result_limit_reached")
            .hasMessageNotContaining("notion-token");
    }

    @Test
    void listBlockChildrenFollowsPagination() {
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
        NotionApiClient client = client();

        List<NotionApiClient.NotionBlock> blocks = client.listBlockChildren("root");

        assertThat(blocks)
            .extracting(NotionApiClient.NotionBlock::id)
            .containsExactly("block-1", "block-2");
        assertThat(blocks.get(0).plainText()).isEqualTo("First");
        assertThat(blocks.get(1).plainText()).isEqualTo("Second");
        assertThat(blocks.get(1).hasChildren()).isTrue();
        assertThat(requests).containsExactly(
            "/v1/blocks/root/children?page_size=100",
            "/v1/blocks/root/children?page_size=100&start_cursor=cursor-2"
        );
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
