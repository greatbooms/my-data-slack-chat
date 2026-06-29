package com.mydata.connectors.notion;

import com.mydata.auth.PrincipalKeys;
import com.mydata.connectors.core.DataSourceConnector;
import com.mydata.connectors.core.DocumentHandler;
import com.mydata.connectors.core.RawAclEntry;
import com.mydata.connectors.core.RawContent;
import com.mydata.connectors.core.RawExternalDocument;
import com.mydata.connectors.core.SyncCursor;
import com.mydata.datasources.DataSourceEntity;
import com.mydata.datasources.DataSourceType;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class NotionPageConnector implements DataSourceConnector {
    public static final String ROOT_PAGE_ID_CONFIG_KEY = "notionRootPageId";
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
    public SyncCursor fetchChanges(DataSourceEntity dataSource, SyncCursor cursor, DocumentHandler handler) {
        String rootPageId = requiredConfig(dataSource, ROOT_PAGE_ID_CONFIG_KEY);
        String principalKey = principalKey(dataSource);

        fetchPage(rootPageId, rootPageId, principalKey, handler, new HashSet<>());
        return cursor;
    }

    private void fetchPage(
        String pageId,
        String rootPageId,
        String principalKey,
        DocumentHandler handler,
        Set<String> visitedPageIds
    ) {
        if (!visitedPageIds.add(pageId)) {
            return;
        }

        NotionApiClient.NotionPage page = notionClient.retrievePage(pageId);
        PageContent pageContent = collectPageContent(pageId);
        String text = documentText(titleOrFallback(page), pageContent.lines());

        handler.handle(new RawExternalDocument(
            page.id(),
            DataSourceType.NOTION,
            titleOrFallback(page),
            page.url(),
            MIME_TYPE,
            page.createdTime(),
            page.lastEditedTime(),
            sha256(text),
            Map.of(
                "notionPageId", page.id(),
                "notionRootPageId", rootPageId
            ),
            new RawContent(text, MIME_TYPE),
            List.of(new RawAclEntry(principalKey, "READ", false, "NOTION"))
        ));

        for (String childPageId : pageContent.childPageIds()) {
            fetchPage(childPageId, rootPageId, principalKey, handler, visitedPageIds);
        }
    }

    private PageContent collectPageContent(String pageId) {
        List<String> lines = new ArrayList<>();
        List<String> childPageIds = new ArrayList<>();
        collectBlocks(notionClient.listBlockChildren(pageId), lines, childPageIds, new HashSet<>());
        return new PageContent(lines, childPageIds);
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
                collectBlocks(notionClient.listBlockChildren(block.id()), lines, childPageIds, visitedBlockIds);
            }
        }
    }

    private String documentText(String title, List<String> lines) {
        List<String> allLines = new ArrayList<>();
        allLines.add(title);
        allLines.addAll(lines);
        return String.join("\n", allLines);
    }

    private String titleOrFallback(NotionApiClient.NotionPage page) {
        if (page.title() != null && !page.title().isBlank()) {
            return page.title().trim();
        }
        return page.id();
    }

    private String principalKey(DataSourceEntity dataSource) {
        return switch (dataSource.getVisibility()) {
            case PRIVATE -> PrincipalKeys.user(dataSource.getOwnerUserId());
            case WORKSPACE -> PrincipalKeys.workspace(dataSource.getWorkspaceId());
        };
    }

    private String requiredConfig(DataSourceEntity dataSource, String key) {
        String value = dataSource.configValue(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("NOTION 설정값이 없습니다: " + key);
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
