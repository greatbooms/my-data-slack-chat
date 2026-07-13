package com.mydata.connectors.notion;

import com.mydata.connectors.core.ConnectorDocumentEvent;
import com.mydata.connectors.core.ConnectorEventSink;
import com.mydata.connectors.core.ConnectorFailureEvent;
import com.mydata.connectors.core.ConnectorFailureStage;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
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
        String rootPageId = optionalConfig(dataSource, ROOT_PAGE_ID_CONFIG_KEY);
        String databaseId = optionalConfig(dataSource, DATABASE_ID_CONFIG_KEY);

        if (rootPageId != null && databaseId != null) {
            throw new IllegalArgumentException("NOTION 설정은 notionRootPageId 또는 notionDatabaseId 중 하나만 사용할 수 있습니다");
        }
        TraversalState state = new TraversalState(dataSource.visibilityPrincipalKey(), sink);
        Deque<TraversalWork> work = new ArrayDeque<>();
        if (databaseId != null) {
            work.addFirst(new DatabaseWork(
                new ResourceReference(databaseId, databaseId),
                List.of(),
                new PageLocation(null, null, null, List.of(), null)
            ));
            traverse(work, state);
            return dataSource.cursor();
        }

        rootPageId = requiredConfig(dataSource, ROOT_PAGE_ID_CONFIG_KEY);
        work.addFirst(new PageWork(
            new ResourceReference(rootPageId, rootPageId),
            new PageLocation(rootPageId, null, null, List.of(), null)
        ));
        traverse(work, state);
        return dataSource.cursor();
    }

    private NotionApiClient.NotionDataSource singleDataSource(NotionApiClient.NotionDatabase database) {
        if (database.dataSources().size() != 1) {
            throw new IllegalArgumentException("Notion database의 data source가 1개여야 합니다");
        }
        return database.dataSources().getFirst();
    }

    private void traverse(Deque<TraversalWork> work, TraversalState state) {
        while (!work.isEmpty()) {
            TraversalWork next = work.removeFirst();
            if (next instanceof PageWork page) {
                processPage(page, work, state);
            } else if (next instanceof DatabaseWork database) {
                processDatabase(database, work, state);
            } else if (next instanceof DatabaseBatchWork batch) {
                processDatabaseBatch(batch, work, state);
            } else {
                throw new IllegalStateException("지원하지 않는 Notion 순회 작업입니다: " + next.getClass());
            }
        }
    }

    private void processPage(PageWork workItem, Deque<TraversalWork> work, TraversalState state) {
        ResourceReference reference = workItem.reference();
        PageLocation location = workItem.location();
        if (!state.visitedPageIds.add(reference.id())) {
            return;
        }

        List<String> referencePath = append(
            location.parentPath(), titleOrFallback(reference.title(), reference.id())
        );
        NotionApiClient.NotionPage page;
        try {
            page = notionClient.retrievePage(reference.id());
        } catch (NotionApiException retrieveFailure) {
            state.sink.onFailure(failure(
                ConnectorItemType.PAGE,
                reference.id(),
                referencePath,
                ConnectorFailureStage.RETRIEVE,
                retrieveFailure.getMessage()
            ));
            return;
        }

        String title = titleOrFallback(page);
        List<String> path = append(location.parentPath(), title);
        PageContent pageContent;
        try {
            pageContent = collectPageContent(page.id());
        } catch (NotionApiException blockFailure) {
            state.sink.onFailure(failure(
                ConnectorItemType.PAGE,
                page.id(),
                path,
                ConnectorFailureStage.LIST_BLOCKS,
                blockFailure.getMessage()
            ));
            return;
        }
        String text = documentText(title, page.properties(), pageContent.lines());
        Map<String, Object> metadata = metadata(
            page,
            location.rootPageId(),
            location.parentPageId(),
            location.parentTitle(),
            path,
            path.size() - 1
        );
        if (location.database() != null) {
            DatabaseContext database = location.database();
            metadata.put("notionDatabaseId", database.id());
            metadata.put("notionDatabaseTitle", database.title());
            metadata.put("notionDataSourceId", database.dataSource().id());
            putIfPresent(metadata, "notionDataSourceName", database.dataSource().name());
        }

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
            List.of(new RawAclEntry(state.principalKey, "READ", false, "NOTION"))
        );
        state.sink.onDocument(new ConnectorDocumentEvent(
            document,
            new ConnectorItemReference(ConnectorItemType.PAGE, page.id(), title, path)
        ));

        for (NotionApiClient.NotionBlock unsupported : pageContent.unsupportedDatabaseBlocks()) {
            List<String> blockPath = append(
                path, titleOrFallback(unsupported.plainText(), unsupported.id())
            );
            state.sink.onFailure(failure(
                ConnectorItemType.BLOCK,
                unsupported.id(),
                blockPath,
                ConnectorFailureStage.RETRIEVE,
                "Notion API가 child_database 블록의 원본 database ID를 제공하지 않았습니다"
            ));
        }

        PageLocation childLocation = new PageLocation(
            location.rootPageId(), page.id(), title, path, location.database()
        );
        for (int index = pageContent.childDatabases().size() - 1; index >= 0; index--) {
            work.addFirst(new DatabaseWork(pageContent.childDatabases().get(index), path, childLocation));
        }
        for (int index = pageContent.childPages().size() - 1; index >= 0; index--) {
            work.addFirst(new PageWork(pageContent.childPages().get(index), childLocation));
        }
    }

    private void processDatabase(
        DatabaseWork workItem,
        Deque<TraversalWork> work,
        TraversalState state
    ) {
        ResourceReference reference = workItem.reference();
        List<String> parentPath = workItem.parentPath();
        PageLocation parent = workItem.parent();
        if (!state.visitedDatabaseIds.add(reference.id())) {
            return;
        }

        List<String> referencePath = append(
            parentPath, titleOrFallback(reference.title(), reference.id())
        );
        NotionApiClient.NotionDatabase database;
        try {
            database = notionClient.retrieveDatabase(reference.id());
        } catch (NotionApiException retrieveFailure) {
            state.sink.onFailure(failure(
                ConnectorItemType.DATABASE,
                reference.id(),
                referencePath,
                ConnectorFailureStage.RETRIEVE,
                databaseReason(retrieveFailure)
            ));
            return;
        }

        String databaseTitle = titleOrFallback(database.title(), database.id());
        List<String> databasePath = append(parentPath, databaseTitle);
        NotionApiClient.NotionDataSource dataSource;
        try {
            dataSource = singleDataSource(database);
        } catch (IllegalArgumentException invalidDatabase) {
            state.sink.onFailure(failure(
                ConnectorItemType.DATABASE,
                reference.id(),
                databasePath,
                ConnectorFailureStage.RETRIEVE,
                invalidDatabase.getMessage()
            ));
            return;
        }

        DatabaseContext context = new DatabaseContext(database.id(), databaseTitle, dataSource);
        String rowParentTitle = parent.parentTitle();
        if (parent.parentPageId() == null && rowParentTitle == null) {
            rowParentTitle = databaseTitle;
        }
        String finalRowParentTitle = rowParentTitle;
        PageLocation rowLocation = new PageLocation(
            parent.rootPageId(),
            parent.parentPageId(),
            finalRowParentTitle,
            databasePath,
            context
        );
        work.addFirst(new DatabaseBatchWork(
            reference,
            databasePath,
            rowLocation,
            dataSource.id(),
            null
        ));
    }

    private void processDatabaseBatch(
        DatabaseBatchWork workItem,
        Deque<TraversalWork> work,
        TraversalState state
    ) {
        NotionClient.Batch<NotionApiClient.NotionPage> batch;
        try {
            batch = notionClient.queryDataSourcePages(workItem.dataSourceId(), workItem.cursor());
        } catch (NotionApiException queryFailure) {
            state.sink.onFailure(failure(
                ConnectorItemType.DATABASE,
                workItem.database().id(),
                workItem.databasePath(),
                ConnectorFailureStage.QUERY,
                databaseReason(queryFailure)
            ));
            return;
        }

        List<ResourceReference> rows = batch.items().stream()
            .map(page -> new ResourceReference(page.id(), page.title()))
            .toList();
        if (batch.nextCursor() != null) {
            work.addFirst(new DatabaseBatchWork(
                workItem.database(),
                workItem.databasePath(),
                workItem.rowLocation(),
                workItem.dataSourceId(),
                batch.nextCursor()
            ));
        }
        for (int index = rows.size() - 1; index >= 0; index--) {
            work.addFirst(new PageWork(rows.get(index), workItem.rowLocation()));
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
        List<ResourceReference> childPages = new ArrayList<>();
        List<ResourceReference> childDatabases = new ArrayList<>();
        List<NotionApiClient.NotionBlock> unsupportedDatabaseBlocks = new ArrayList<>();
        Set<String> visitedBlockIds = new HashSet<>();
        Deque<BlockWork> work = new ArrayDeque<>();
        work.addFirst(new BlockBatchWork(pageId, null));
        while (!work.isEmpty()) {
            BlockWork next = work.removeFirst();
            if (next instanceof BlockBatchWork blockBatch) {
                NotionClient.Batch<NotionApiClient.NotionBlock> batch = notionClient.listBlockChildren(
                    blockBatch.blockId(), blockBatch.cursor()
                );
                if (batch.nextCursor() != null) {
                    work.addFirst(new BlockBatchWork(blockBatch.blockId(), batch.nextCursor()));
                }
                for (int index = batch.items().size() - 1; index >= 0; index--) {
                    work.addFirst(new BlockItemWork(batch.items().get(index)));
                }
                continue;
            }
            if (!(next instanceof BlockItemWork blockItem)) {
                throw new IllegalStateException("지원하지 않는 Notion block 순회 작업입니다: " + next.getClass());
            }
            NotionApiClient.NotionBlock block = blockItem.block();
            if (!visitedBlockIds.add(block.id())) {
                continue;
            }

            if (block.plainText() != null && !block.plainText().isBlank()) {
                lines.add(block.plainText().trim());
            }
            if ("child_page".equals(block.type())) {
                childPages.add(new ResourceReference(block.id(), block.plainText()));
                continue;
            }
            if ("child_database".equals(block.type())) {
                childDatabases.add(new ResourceReference(block.id(), block.plainText()));
                continue;
            }
            if ("unsupported".equals(block.type())
                && "child_database".equals(block.underlyingType())) {
                unsupportedDatabaseBlocks.add(block);
                continue;
            }
            if ("link_to_page".equals(block.type())) {
                continue;
            }
            if (block.hasChildren()) {
                work.addFirst(new BlockBatchWork(block.id(), null));
            }
        }
        return new PageContent(lines, childPages, childDatabases, unsupportedDatabaseBlocks);
    }

    private ConnectorFailureEvent failure(
        ConnectorItemType type,
        String externalId,
        List<String> path,
        ConnectorFailureStage stage,
        String reason
    ) {
        String title = path.isEmpty() ? externalId : path.getLast();
        return new ConnectorFailureEvent(
            new ConnectorItemReference(type, externalId, title, path),
            stage,
            reason
        );
    }

    private String databaseReason(NotionApiException error) {
        if (Integer.valueOf(404).equals(error.statusCode())
            && "object_not_found".equals(error.code())) {
            return error.getMessage() + "; 원본 database를 integration에 공유하세요";
        }
        return error.getMessage();
    }

    private List<String> append(List<String> values, String value) {
        List<String> appended = new ArrayList<>(values);
        appended.add(value);
        return List.copyOf(appended);
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

    private static final class TraversalState {
        private final String principalKey;
        private final ConnectorEventSink sink;
        private final Set<String> visitedPageIds = new HashSet<>();
        private final Set<String> visitedDatabaseIds = new HashSet<>();

        private TraversalState(String principalKey, ConnectorEventSink sink) {
            this.principalKey = principalKey;
            this.sink = sink;
        }
    }

    private record ResourceReference(String id, String title) {
    }

    private record DatabaseContext(
        String id,
        String title,
        NotionApiClient.NotionDataSource dataSource
    ) {
    }

    private record PageLocation(
        String rootPageId,
        String parentPageId,
        String parentTitle,
        List<String> parentPath,
        DatabaseContext database
    ) {
    }

    private record PageContent(
        List<String> lines,
        List<ResourceReference> childPages,
        List<ResourceReference> childDatabases,
        List<NotionApiClient.NotionBlock> unsupportedDatabaseBlocks
    ) {
    }

    private interface TraversalWork {
    }

    private record PageWork(ResourceReference reference, PageLocation location) implements TraversalWork {
    }

    private record DatabaseWork(
        ResourceReference reference,
        List<String> parentPath,
        PageLocation parent
    ) implements TraversalWork {
    }

    private record DatabaseBatchWork(
        ResourceReference database,
        List<String> databasePath,
        PageLocation rowLocation,
        String dataSourceId,
        String cursor
    ) implements TraversalWork {
    }

    private interface BlockWork {
    }

    private record BlockBatchWork(String blockId, String cursor) implements BlockWork {
    }

    private record BlockItemWork(NotionApiClient.NotionBlock block) implements BlockWork {
    }
}
