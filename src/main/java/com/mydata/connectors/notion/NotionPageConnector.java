package com.mydata.connectors.notion;

import com.mydata.connectors.core.ConnectorDocumentEvent;
import com.mydata.connectors.core.ConnectorEventSink;
import com.mydata.connectors.core.ConnectorItemReference;
import com.mydata.connectors.core.ConnectorItemType;
import com.mydata.connectors.core.DataSourceConnector;
import com.mydata.connectors.core.DataSourceSnapshot;
import com.mydata.connectors.core.RawAclEntry;
import com.mydata.connectors.core.RawContent;
import com.mydata.connectors.core.RawExternalDocument;
import com.mydata.connectors.core.SyncCursor;
import com.mydata.datasources.DataSourceType;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class NotionPageConnector implements DataSourceConnector {
    public static final String ROOT_PAGE_ID_CONFIG_KEY = "notionRootPageId";
    public static final String DATABASE_ID_CONFIG_KEY = "notionDatabaseId";
    private static final String MIME_TYPE = "text/plain";

    private final NotionClient notionClient;

    public NotionPageConnector(NotionClient notionClient) {
        this.notionClient = notionClient;
    }

    @Override
    public DataSourceType supports() {
        return DataSourceType.NOTION;
    }

    @Override
    public SyncCursor fetchChanges(DataSourceSnapshot dataSource, ConnectorEventSink sink) {
        String principalKey = dataSource.visibilityPrincipalKey();
        String rootPageId = optionalConfig(dataSource, ROOT_PAGE_ID_CONFIG_KEY);
        String databaseId = optionalConfig(dataSource, DATABASE_ID_CONFIG_KEY);

        if (rootPageId != null && databaseId != null) {
            throw new IllegalArgumentException("NOTION 설정은 notionRootPageId 또는 notionDatabaseId 중 하나만 사용할 수 있습니다");
        }
        if (databaseId != null) {
            fetchDatabase(databaseId, principalKey, sink);
            return dataSource.cursor();
        }

        rootPageId = requiredConfig(dataSource, ROOT_PAGE_ID_CONFIG_KEY);
        fetchPage(rootPageId, rootPageId, null, null, List.of(), 0, principalKey, sink, new HashSet<>());
        return dataSource.cursor();
    }

    private void fetchDatabase(String databaseId, String principalKey, ConnectorEventSink sink) {
        NotionApiClient.NotionDatabase database = notionClient.retrieveDatabase(databaseId);
        NotionApiClient.NotionDataSource dataSource = singleDataSource(database);
        String databaseTitle = titleOrFallback(database.title(), database.id());
        Set<String> visitedPageIds = new HashSet<>();
        notionClient.queryDataSourcePages(dataSource.id(), pages -> {
            for (NotionApiClient.NotionPage page : pages) {
                fetchDatabasePage(
                    database,
                    databaseTitle,
                    dataSource,
                    page,
                    null,
                    databaseTitle,
                    List.of(databaseTitle),
                    1,
                    principalKey,
                    sink,
                    visitedPageIds
                );
            }
        });
    }

    private NotionApiClient.NotionDataSource singleDataSource(NotionApiClient.NotionDatabase database) {
        if (database.dataSources().size() != 1) {
            throw new IllegalArgumentException("Notion database의 data source가 1개여야 합니다");
        }
        return database.dataSources().getFirst();
    }

    private void fetchDatabasePage(
        NotionApiClient.NotionDatabase database,
        String databaseTitle,
        NotionApiClient.NotionDataSource dataSource,
        NotionApiClient.NotionPage page,
        String parentPageId,
        String parentTitle,
        List<String> parentPath,
        int depth,
        String principalKey,
        ConnectorEventSink sink,
        Set<String> visitedPageIds
    ) {
        if (!visitedPageIds.add(page.id())) {
            return;
        }

        String title = titleOrFallback(page);
        List<String> path = new ArrayList<>(parentPath);
        path.add(title);
        PageContent pageContent = collectPageContent(page.id());
        String text = documentText(title, page.properties(), pageContent.lines());
        Map<String, Object> metadata = metadata(page, null, parentPageId, parentTitle, path, depth);
        metadata.put("notionDatabaseId", database.id());
        metadata.put("notionDatabaseTitle", databaseTitle);
        metadata.put("notionDataSourceId", dataSource.id());
        putIfPresent(metadata, "notionDataSourceName", dataSource.name());

        RawExternalDocument document = new RawExternalDocument(
            page.id(),
            DataSourceType.NOTION,
            title,
            page.url(),
            MIME_TYPE,
            page.createdTime(),
            page.lastEditedTime(),
            sha256(text),
            metadata,
            new RawContent(text, MIME_TYPE),
            List.of(new RawAclEntry(principalKey, "READ", false, "NOTION"))
        );
        sink.onDocument(new ConnectorDocumentEvent(
            document,
            new ConnectorItemReference(ConnectorItemType.PAGE, page.id(), title, path)
        ));

        for (String childPageId : pageContent.childPageIds()) {
            NotionApiClient.NotionPage childPage = notionClient.retrievePage(childPageId);
            fetchDatabasePage(
                database,
                databaseTitle,
                dataSource,
                childPage,
                page.id(),
                title,
                path,
                depth + 1,
                principalKey,
                sink,
                visitedPageIds
            );
        }
    }

    private void fetchPage(
        String pageId,
        String rootPageId,
        String parentPageId,
        String parentTitle,
        List<String> parentPath,
        int depth,
        String principalKey,
        ConnectorEventSink sink,
        Set<String> visitedPageIds
    ) {
        if (!visitedPageIds.add(pageId)) {
            return;
        }

        NotionApiClient.NotionPage page = notionClient.retrievePage(pageId);
        String title = titleOrFallback(page);
        List<String> path = new ArrayList<>(parentPath);
        path.add(title);
        PageContent pageContent = collectPageContent(pageId);
        String text = documentText(title, page.properties(), pageContent.lines());

        RawExternalDocument document = new RawExternalDocument(
            page.id(),
            DataSourceType.NOTION,
            title,
            page.url(),
            MIME_TYPE,
            page.createdTime(),
            page.lastEditedTime(),
            sha256(text),
            metadata(page, rootPageId, parentPageId, parentTitle, path, depth),
            new RawContent(text, MIME_TYPE),
            List.of(new RawAclEntry(principalKey, "READ", false, "NOTION"))
        );
        sink.onDocument(new ConnectorDocumentEvent(
            document,
            new ConnectorItemReference(ConnectorItemType.PAGE, page.id(), title, path)
        ));

        for (String childPageId : pageContent.childPageIds()) {
            fetchPage(childPageId, rootPageId, page.id(), title, path, depth + 1, principalKey, sink, visitedPageIds);
        }
    }

    private Map<String, Object> metadata(
        NotionApiClient.NotionPage page,
        String rootPageId,
        String parentPageId,
        String parentTitle,
        List<String> path,
        int depth
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("notionPageId", page.id());
        putIfPresent(metadata, "notionRootPageId", rootPageId);
        if (parentPageId != null) {
            metadata.put("notionParentPageId", parentPageId);
        }
        if (parentTitle != null) {
            metadata.put("notionParentTitle", parentTitle);
        }
        metadata.put("notionDepth", depth);
        metadata.put("notionPath", List.copyOf(path));
        putIfPresent(metadata, "notionApiParentType", page.parentType());
        putIfPresent(metadata, "notionApiParentId", page.parentId());
        putIfPresent(metadata, "notionPublicUrl", page.publicUrl());
        metadata.put("notionInTrash", page.inTrash());
        putIfPresent(metadata, "notionCreatedByUserId", page.createdByUserId());
        putIfPresent(metadata, "notionLastEditedByUserId", page.lastEditedByUserId());
        if (!page.properties().isEmpty()) {
            metadata.put("notionProperties", new LinkedHashMap<>(page.properties()));
        }
        return metadata;
    }

    private void putIfPresent(Map<String, Object> metadata, String key, String value) {
        if (value != null && !value.isBlank()) {
            metadata.put(key, value);
        }
    }

    private PageContent collectPageContent(String pageId) {
        List<String> lines = new ArrayList<>();
        List<String> childPageIds = new ArrayList<>();
        collectBlockChildren(pageId, lines, childPageIds, new HashSet<>());
        return new PageContent(lines, childPageIds);
    }

    private void collectBlockChildren(
        String blockId,
        List<String> lines,
        List<String> childPageIds,
        Set<String> visitedBlockIds
    ) {
        notionClient.listBlockChildren(blockId, blocks ->
            collectBlocks(blocks, lines, childPageIds, visitedBlockIds)
        );
    }

    private void collectBlocks(
        List<NotionApiClient.NotionBlock> blocks,
        List<String> lines,
        List<String> childPageIds,
        Set<String> visitedBlockIds
    ) {
        for (NotionApiClient.NotionBlock block : blocks) {
            if (!visitedBlockIds.add(block.id())) {
                continue;
            }

            if (block.plainText() != null && !block.plainText().isBlank()) {
                lines.add(block.plainText().trim());
            }
            if ("child_page".equals(block.type())) {
                childPageIds.add(block.id());
                continue;
            }
            if (block.hasChildren()) {
                collectBlockChildren(block.id(), lines, childPageIds, visitedBlockIds);
            }
        }
    }

    private String documentText(String title, Map<String, String> properties, List<String> lines) {
        List<String> allLines = new ArrayList<>();
        allLines.add(title);
        properties.forEach((key, value) -> allLines.add(key + ": " + value));
        allLines.addAll(lines);
        return String.join("\n", allLines);
    }

    private String titleOrFallback(NotionApiClient.NotionPage page) {
        return titleOrFallback(page.title(), page.id());
    }

    private String titleOrFallback(String title, String fallback) {
        if (title != null && !title.isBlank()) {
            return title.trim();
        }
        return fallback;
    }

    private String requiredConfig(DataSourceSnapshot dataSource, String key) {
        String value = optionalConfig(dataSource, key);
        if (value == null) {
            throw new IllegalArgumentException("NOTION 설정값이 없습니다: " + key);
        }
        return value;
    }

    private String optionalConfig(DataSourceSnapshot dataSource, String key) {
        String value = dataSource.configValue(key);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다", exception);
        }
    }

    private record PageContent(List<String> lines, List<String> childPageIds) {
    }
}
