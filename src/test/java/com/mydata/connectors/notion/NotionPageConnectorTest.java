package com.mydata.connectors.notion;

import com.mydata.auth.PrincipalKeys;
import com.mydata.connectors.core.RawExternalDocument;
import com.mydata.connectors.core.SyncCursor;
import com.mydata.datasources.DataSourceEntity;
import com.mydata.datasources.DataSourceStatus;
import com.mydata.datasources.DataSourceType;
import com.mydata.datasources.DataSourceVisibility;
import com.mydata.datasources.SyncMode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotionPageConnectorTest {
    @Test
    void fetchChangesEmitsRootAndChildPagesWithAclAndPlainTextContent() {
        UUID workspaceId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        DataSourceEntity dataSource = dataSource(workspaceId, ownerId, DataSourceVisibility.PRIVATE);
        dataSource.putConfig(NotionPageConnector.ROOT_PAGE_ID_CONFIG_KEY, "root-page");
        FakeNotionClient notion = new FakeNotionClient();
        notion.page("root-page", "Root Plan", "https://notion.so/root-page");
        notion.page("child-page", "Child Spec", "https://notion.so/child-page");
        notion.blocks("root-page",
            block("block-1", "heading_1", "Intro", false),
            block("block-2", "paragraph", "Root body", false),
            block("child-page", "child_page", "Child Spec", false)
        );
        notion.blocks("child-page",
            block("block-3", "paragraph", "Child body", false)
        );
        NotionPageConnector connector = new NotionPageConnector(notion);
        List<RawExternalDocument> documents = new ArrayList<>();

        SyncCursor returnedCursor = connector.fetchChanges(dataSource, new SyncCursor(Map.of()), documents::add);

        assertThat(returnedCursor.value()).isEmpty();
        assertThat(connector.supports()).isEqualTo(DataSourceType.NOTION);
        assertThat(documents)
            .extracting(RawExternalDocument::externalId)
            .containsExactly("root-page", "child-page");

        RawExternalDocument root = documents.get(0);
        assertThat(root.sourceType()).isEqualTo(DataSourceType.NOTION);
        assertThat(root.title()).isEqualTo("Root Plan");
        assertThat(root.uri()).isEqualTo("https://notion.so/root-page");
        assertThat(root.mimeType()).isEqualTo("text/plain");
        assertThat(root.externalUpdatedAt()).isEqualTo(Instant.parse("2026-06-02T00:00:00Z"));
        assertThat(root.content().text()).isEqualTo("""
            Root Plan
            Intro
            Root body
            Child Spec
            """.stripTrailing());
        assertThat(root.contentHash()).isNotBlank();
        assertThat(root.metadata())
            .containsEntry("notionPageId", "root-page")
            .containsEntry("notionRootPageId", "root-page")
            .containsEntry("notionDepth", 0)
            .containsEntry("notionPath", List.of("Root Plan"))
            .containsEntry("notionApiParentType", "workspace")
            .containsEntry("notionPublicUrl", "https://public.notion.site/root-page")
            .containsEntry("notionInTrash", false)
            .containsEntry("notionCreatedByUserId", "creator-root-page")
            .containsEntry("notionLastEditedByUserId", "editor-root-page");
        assertThat(root.metadata())
            .doesNotContainKey("notionParentPageId")
            .doesNotContainKey("notionParentTitle");
        assertThat(root.aclEntries()).singleElement().satisfies(acl -> {
            assertThat(acl.principalKey()).isEqualTo(PrincipalKeys.user(ownerId));
            assertThat(acl.permission()).isEqualTo("READ");
            assertThat(acl.inherited()).isFalse();
            assertThat(acl.source()).isEqualTo("NOTION");
        });

        RawExternalDocument child = documents.get(1);
        assertThat(child.title()).isEqualTo("Child Spec");
        assertThat(child.content().text()).isEqualTo("""
            Child Spec
            Child body
            """.stripTrailing());
        assertThat(child.metadata())
            .containsEntry("notionPageId", "child-page")
            .containsEntry("notionRootPageId", "root-page")
            .containsEntry("notionParentPageId", "root-page")
            .containsEntry("notionParentTitle", "Root Plan")
            .containsEntry("notionDepth", 1)
            .containsEntry("notionPath", List.of("Root Plan", "Child Spec"))
            .containsEntry("notionApiParentType", "page_id")
            .containsEntry("notionApiParentId", "root-page")
            .containsEntry("notionPublicUrl", "https://public.notion.site/child-page")
            .containsEntry("notionInTrash", false)
            .containsEntry("notionCreatedByUserId", "creator-child-page")
            .containsEntry("notionLastEditedByUserId", "editor-child-page");
    }

    @Test
    void workspaceVisibilityUsesWorkspacePrincipal() {
        UUID workspaceId = UUID.randomUUID();
        DataSourceEntity dataSource = dataSource(workspaceId, UUID.randomUUID(), DataSourceVisibility.WORKSPACE);
        dataSource.putConfig(NotionPageConnector.ROOT_PAGE_ID_CONFIG_KEY, "root-page");
        FakeNotionClient notion = new FakeNotionClient();
        notion.page("root-page", "Root Plan", "https://notion.so/root-page");
        notion.blocks("root-page");
        NotionPageConnector connector = new NotionPageConnector(notion);
        List<RawExternalDocument> documents = new ArrayList<>();

        connector.fetchChanges(dataSource, new SyncCursor(Map.of()), documents::add);

        assertThat(documents).singleElement()
            .satisfies(document -> assertThat(document.aclEntries()).singleElement()
                .satisfies(acl -> assertThat(acl.principalKey()).isEqualTo(PrincipalKeys.workspace(workspaceId))));
    }

    @Test
    void fetchChangesRequiresRootPageId() {
        NotionPageConnector connector = new NotionPageConnector(new FakeNotionClient());
        DataSourceEntity dataSource = dataSource(UUID.randomUUID(), UUID.randomUUID(), DataSourceVisibility.PRIVATE);

        assertThatThrownBy(() -> connector.fetchChanges(dataSource, new SyncCursor(Map.of()), document -> {
        }))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(NotionPageConnector.ROOT_PAGE_ID_CONFIG_KEY);
    }

    private static DataSourceEntity dataSource(UUID workspaceId, UUID ownerId, DataSourceVisibility visibility) {
        DataSourceEntity dataSource = DataSourceEntity.create(
            workspaceId,
            DataSourceType.NOTION,
            "Notion",
            DataSourceStatus.ACTIVE,
            SyncMode.MANUAL
        );
        dataSource.assignOwner(ownerId);
        dataSource.changeVisibility(visibility);
        return dataSource;
    }

    private static NotionApiClient.NotionBlock block(
        String id,
        String type,
        String plainText,
        boolean hasChildren
    ) {
        return new NotionApiClient.NotionBlock(id, type, plainText, hasChildren);
    }

    private static class FakeNotionClient implements NotionClient {
        private final Map<String, NotionApiClient.NotionPage> pages = new java.util.LinkedHashMap<>();
        private final Map<String, List<NotionApiClient.NotionBlock>> blockChildren = new java.util.LinkedHashMap<>();

        void page(String id, String title, String url) {
            pages.put(id, new NotionApiClient.NotionPage(
                id,
                title,
                url,
                "https://public.notion.site/" + id,
                Instant.parse("2026-06-01T00:00:00Z"),
                Instant.parse("2026-06-02T00:00:00Z"),
                "creator-" + id,
                "editor-" + id,
                "root-page".equals(id) ? "workspace" : "page_id",
                "root-page".equals(id) ? null : "root-page",
                false
            ));
        }

        void blocks(String id, NotionApiClient.NotionBlock... blocks) {
            blockChildren.put(id, List.of(blocks));
        }

        @Override
        public NotionApiClient.NotionPage retrievePage(String pageId) {
            return pages.get(pageId);
        }

        @Override
        public List<NotionApiClient.NotionBlock> listBlockChildren(String blockId) {
            return blockChildren.getOrDefault(blockId, List.of());
        }
    }
}
