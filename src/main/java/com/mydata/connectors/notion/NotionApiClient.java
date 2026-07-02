package com.mydata.connectors.notion;

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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class NotionApiClient implements NotionClient {
    private static final int PAGE_SIZE = 100;
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI baseUri;
    private final String apiToken;
    private final String notionVersion;
    private final Duration requestTimeout;

    public NotionApiClient(
        HttpClient httpClient,
        ObjectMapper objectMapper,
        URI baseUri,
        String apiToken,
        String notionVersion
    ) {
        this(httpClient, objectMapper, baseUri, apiToken, notionVersion, DEFAULT_REQUEST_TIMEOUT);
    }

    public NotionApiClient(
        HttpClient httpClient,
        ObjectMapper objectMapper,
        URI baseUri,
        String apiToken,
        String notionVersion,
        Duration requestTimeout
    ) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.baseUri = baseUri;
        this.apiToken = apiToken;
        this.notionVersion = notionVersion;
        this.requestTimeout = requestTimeout == null ? DEFAULT_REQUEST_TIMEOUT : requestTimeout;
    }

    @Override
    public NotionPage retrievePage(String pageId) {
        JsonNode root = getJson("/v1/pages/" + pathSegment(pageId));

        return new NotionPage(
            root.path("id").asString(),
            extractPageTitle(root.path("properties")),
            blankToNull(root.path("url").asString(null)),
            blankToNull(root.path("public_url").asString(null)),
            parseInstant(root.path("created_time").asString(null)),
            parseInstant(root.path("last_edited_time").asString(null)),
            blankToNull(root.path("created_by").path("id").asString(null)),
            blankToNull(root.path("last_edited_by").path("id").asString(null)),
            extractParentType(root.path("parent")),
            extractParentId(root.path("parent")),
            root.path("in_trash").asBoolean(false)
        );
    }

    @Override
    public List<NotionBlock> listBlockChildren(String blockId) {
        List<NotionBlock> blocks = new ArrayList<>();
        String nextCursor = null;
        boolean hasMore;

        do {
            String path = "/v1/blocks/" + pathSegment(blockId) + "/children?page_size=" + PAGE_SIZE;
            if (nextCursor != null) {
                path += "&start_cursor=" + queryParam(nextCursor);
            }

            JsonNode root = getJson(path);
            JsonNode results = root.path("results");
            if (results.isArray()) {
                for (JsonNode result : results) {
                    blocks.add(toBlock(result));
                }
            }

            hasMore = root.path("has_more").asBoolean(false);
            nextCursor = blankToNull(root.path("next_cursor").asString(null));
        } while (hasMore && nextCursor != null);

        return blocks;
    }

    private JsonNode getJson(String pathAndQuery) {
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(pathAndQuery))
            .GET()
            .timeout(requestTimeout)
            .header("Authorization", "Bearer " + apiToken)
            .header("Notion-Version", notionVersion)
            .header("Accept", "application/json")
            .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new NotionApiException("Notion API 요청에 실패했습니다", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new NotionApiException("Notion API 요청이 중단되었습니다", exception);
        }

        JsonNode root = readJson(response.body());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String code = blankToNull(root.path("code").asString(null));
            throw new NotionApiException(
                "Notion API 오류: status=" + response.statusCode() + ", code=" + (code == null ? "unknown" : code)
            );
        }
        return root;
    }

    private JsonNode readJson(String body) {
        try {
            return objectMapper.readTree(body == null ? "" : body);
        } catch (JacksonException exception) {
            throw new NotionApiException("Notion API 응답 JSON을 해석하지 못했습니다", exception);
        }
    }

    private NotionBlock toBlock(JsonNode block) {
        String type = block.path("type").asString();
        return new NotionBlock(
            block.path("id").asString(),
            type,
            extractBlockPlainText(block, type),
            block.path("has_children").asBoolean(false)
        );
    }

    private String extractPageTitle(JsonNode properties) {
        if (!properties.isObject()) {
            return "";
        }

        for (JsonNode property : properties.values()) {
            if ("title".equals(property.path("type").asString())) {
                return joinPlainText(property.path("title"));
            }
        }
        return "";
    }

    private String extractBlockPlainText(JsonNode block, String type) {
        JsonNode typedBlock = block.path(type);
        String richText = joinPlainText(typedBlock.path("rich_text"));
        if (!richText.isBlank()) {
            return richText;
        }
        if ("child_page".equals(type)) {
            return typedBlock.path("title").asString("");
        }
        return "";
    }

    private String extractParentType(JsonNode parent) {
        return blankToNull(parent.path("type").asString(null));
    }

    private String extractParentId(JsonNode parent) {
        String type = extractParentType(parent);
        if (type == null || "workspace".equals(type)) {
            return null;
        }
        return blankToNull(parent.path(type).asString(null));
    }

    private String joinPlainText(JsonNode richTextArray) {
        if (!richTextArray.isArray()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (JsonNode richText : richTextArray) {
            String plainText = richText.path("plain_text").asString("");
            if (!plainText.isEmpty()) {
                builder.append(plainText);
            }
        }
        return builder.toString();
    }

    private Instant parseInstant(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? null : Instant.parse(normalized);
    }

    private String pathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
            .replace("+", "%20");
    }

    private String queryParam(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public record NotionPage(
        String id,
        String title,
        String url,
        String publicUrl,
        Instant createdTime,
        Instant lastEditedTime,
        String createdByUserId,
        String lastEditedByUserId,
        String parentType,
        String parentId,
        boolean inTrash
    ) {
        public NotionPage(String id, String title, String url, Instant createdTime, Instant lastEditedTime) {
            this(id, title, url, null, createdTime, lastEditedTime, null, null, null, null, false);
        }
    }

    public record NotionBlock(
        String id,
        String type,
        String plainText,
        boolean hasChildren
    ) {
    }
}
