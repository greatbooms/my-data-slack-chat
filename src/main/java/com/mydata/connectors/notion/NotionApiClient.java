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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

        return toPage(root);
    }

    @Override
    public NotionDatabase retrieveDatabase(String databaseId) {
        JsonNode root = getJson("/v1/databases/" + pathSegment(databaseId));
        List<NotionDataSource> dataSources = new ArrayList<>();
        JsonNode dataSourceNodes = root.path("data_sources");
        if (dataSourceNodes.isArray()) {
            for (JsonNode dataSource : dataSourceNodes) {
                dataSources.add(new NotionDataSource(
                    dataSource.path("id").asString(),
                    blankToNull(dataSource.path("name").asString(null))
                ));
            }
        }

        return new NotionDatabase(
            root.path("id").asString(),
            joinPlainText(root.path("title")),
            blankToNull(root.path("url").asString(null)),
            dataSources
        );
    }

    @Override
    public List<NotionPage> queryDataSourcePages(String dataSourceId) {
        List<NotionPage> pages = new ArrayList<>();
        String nextCursor = null;
        boolean hasMore;

        do {
            JsonNode root = postJson(
                "/v1/data_sources/" + pathSegment(dataSourceId) + "/query",
                queryDataSourceRequestBody(nextCursor)
            );
            rejectIncompleteQuery(root);
            JsonNode results = root.path("results");
            if (results.isArray()) {
                for (JsonNode result : results) {
                    if ("page".equals(result.path("object").asString())) {
                        pages.add(toPage(result));
                    }
                }
            }

            hasMore = root.path("has_more").asBoolean(false);
            nextCursor = blankToNull(root.path("next_cursor").asString(null));
        } while (hasMore && nextCursor != null);

        return pages;
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

        return sendJson(request);
    }

    private JsonNode postJson(String pathAndQuery, String requestBody) {
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(pathAndQuery))
            .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
            .timeout(requestTimeout)
            .header("Authorization", "Bearer " + apiToken)
            .header("Notion-Version", notionVersion)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .build();

        return sendJson(request);
    }

    private JsonNode sendJson(HttpRequest request) {
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

    private String queryDataSourceRequestBody(String nextCursor) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("result_type", "page");
            body.put("page_size", PAGE_SIZE);
            if (nextCursor != null) {
                body.put("start_cursor", nextCursor);
            }
            return objectMapper.writeValueAsString(body);
        } catch (Exception exception) {
            throw new NotionApiException("Notion API 요청 JSON을 생성하지 못했습니다", exception);
        }
    }

    private void rejectIncompleteQuery(JsonNode root) {
        JsonNode requestStatus = root.path("request_status");
        if (!"incomplete".equals(requestStatus.path("type").asString())) {
            return;
        }

        String reason = blankToNull(requestStatus.path("incomplete_reason").asString(null));
        throw new NotionApiException(
            "Notion data source query 결과가 완전하지 않습니다: "
                + (reason == null ? "unknown" : reason)
        );
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

    private NotionPage toPage(JsonNode root) {
        JsonNode properties = root.path("properties");
        return new NotionPage(
            root.path("id").asString(),
            extractPageTitle(properties),
            blankToNull(root.path("url").asString(null)),
            blankToNull(root.path("public_url").asString(null)),
            parseInstant(root.path("created_time").asString(null)),
            parseInstant(root.path("last_edited_time").asString(null)),
            blankToNull(root.path("created_by").path("id").asString(null)),
            blankToNull(root.path("last_edited_by").path("id").asString(null)),
            extractParentType(root.path("parent")),
            extractParentId(root.path("parent")),
            root.path("in_trash").asBoolean(false),
            extractPageProperties(properties)
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

    private Map<String, String> extractPageProperties(JsonNode properties) {
        Map<String, String> values = new LinkedHashMap<>();
        if (!properties.isObject()) {
            return values;
        }

        for (Map.Entry<String, JsonNode> entry : properties.properties()) {
            JsonNode property = entry.getValue();
            if ("title".equals(property.path("type").asString())) {
                continue;
            }
            String value = extractPropertyPlainText(property);
            if (value != null) {
                values.put(entry.getKey(), value);
            }
        }
        return values;
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

    private String extractPropertyPlainText(JsonNode property) {
        String type = property.path("type").asString();
        JsonNode typed = property.path(type);
        String value = switch (type) {
            case "rich_text" -> joinPlainText(typed);
            case "select", "status" -> typed.path("name").asString("");
            case "multi_select" -> joinNames(typed);
            case "checkbox" -> String.valueOf(typed.asBoolean(false));
            case "number" -> typed.isNumber() ? typed.asString() : "";
            case "date" -> dateText(typed);
            case "people" -> joinPeople(typed);
            case "url", "email", "phone_number" -> typed.asString("");
            case "relation" -> joinIds(typed);
            case "files" -> joinNames(typed);
            case "formula" -> extractFormulaPlainText(typed);
            default -> "";
        };
        return blankToNull(value);
    }

    private String extractFormulaPlainText(JsonNode formula) {
        String type = formula.path("type").asString();
        JsonNode typed = formula.path(type);
        return switch (type) {
            case "string" -> typed.asString("");
            case "boolean" -> String.valueOf(typed.asBoolean(false));
            case "number" -> typed.isNumber() ? typed.asString() : "";
            case "date" -> dateText(typed);
            default -> "";
        };
    }

    private String dateText(JsonNode date) {
        String start = blankToNull(date.path("start").asString(null));
        String end = blankToNull(date.path("end").asString(null));
        if (start == null) {
            return "";
        }
        return end == null ? start : start + " - " + end;
    }

    private String joinNames(JsonNode array) {
        if (!array.isArray()) {
            return "";
        }
        List<String> names = new ArrayList<>();
        for (JsonNode item : array) {
            String name = blankToNull(item.path("name").asString(null));
            if (name != null) {
                names.add(name);
            }
        }
        return String.join(", ", names);
    }

    private String joinIds(JsonNode array) {
        if (!array.isArray()) {
            return "";
        }
        List<String> ids = new ArrayList<>();
        for (JsonNode item : array) {
            String id = blankToNull(item.path("id").asString(null));
            if (id != null) {
                ids.add(id);
            }
        }
        return String.join(", ", ids);
    }

    private String joinPeople(JsonNode array) {
        if (!array.isArray()) {
            return "";
        }
        List<String> people = new ArrayList<>();
        for (JsonNode item : array) {
            String name = blankToNull(item.path("name").asString(null));
            if (name != null) {
                people.add(name);
                continue;
            }
            String id = blankToNull(item.path("id").asString(null));
            if (id != null) {
                people.add(id);
            }
        }
        return String.join(", ", people);
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
        boolean inTrash,
        Map<String, String> properties
    ) {
        public NotionPage {
            properties = properties == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(properties));
        }

        public NotionPage(
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
            this(id, title, url, publicUrl, createdTime, lastEditedTime, createdByUserId, lastEditedByUserId,
                parentType, parentId, inTrash, Map.of());
        }

        public NotionPage(String id, String title, String url, Instant createdTime, Instant lastEditedTime) {
            this(id, title, url, null, createdTime, lastEditedTime, null, null, null, null, false, Map.of());
        }
    }

    public record NotionDatabase(
        String id,
        String title,
        String url,
        List<NotionDataSource> dataSources
    ) {
        public NotionDatabase {
            dataSources = dataSources == null ? List.of() : List.copyOf(dataSources);
        }
    }

    public record NotionDataSource(
        String id,
        String name
    ) {
    }

    public record NotionBlock(
        String id,
        String type,
        String plainText,
        boolean hasChildren
    ) {
    }
}
