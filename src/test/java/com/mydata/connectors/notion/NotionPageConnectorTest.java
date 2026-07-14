package com.mydata.connectors.notion;

import com.mydata.auth.PrincipalKeys;
import com.mydata.connectors.core.ConnectorDocumentEvent;
import com.mydata.connectors.core.ConnectorEventSink;
import com.mydata.connectors.core.ConnectorFailureEvent;
import com.mydata.connectors.core.ConnectorFailureStage;
import com.mydata.connectors.core.ConnectorItemReference;
import com.mydata.connectors.core.ConnectorItemType;
import com.mydata.connectors.core.ConnectorReconciliationMode;
import com.mydata.connectors.core.DataSourceSnapshot;
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
    private static final int DEEP_CHAIN_LENGTH = 1_501;

    @Test
    void declaresFullSnapshotReconciliation() {
        NotionPageConnector connector = new NotionPageConnector(new FakeNotionClient());

        assertThat(connector.reconciliationMode())
            .isEqualTo(ConnectorReconciliationMode.FULL_SNAPSHOT);
    }

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
        List<ConnectorDocumentEvent> events = new ArrayList<>();

        SyncCursor returnedCursor = connector.fetchChanges(
            snapshot(dataSource, new SyncCursor(Map.of())),
            recordingSink(events)
        );
        List<RawExternalDocument> documents = events.stream().map(ConnectorDocumentEvent::document).toList();

        assertThat(returnedCursor.value()).isEmpty();
        assertThat(connector.supports()).isEqualTo(DataSourceType.NOTION);
        assertThat(documents)
            .extracting(RawExternalDocument::externalId)
            .containsExactly("root-page", "child-page");
        assertThat(events)
            .extracting(ConnectorDocumentEvent::reference)
            .extracting(ConnectorItemReference::type)
            .containsOnly(ConnectorItemType.PAGE);
        assertThat(events)
            .extracting(event -> event.reference().qualifiedExternalId())
            .containsExactly("page:root-page", "page:child-page");
        assertThat(events)
            .extracting(event -> event.reference().path())
            .containsExactly(List.of("Root Plan"), List.of("Root Plan", "Child Spec"));

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

        connector.fetchChanges(
            snapshot(dataSource, new SyncCursor(Map.of())),
            documentSink(documents)
        );

        assertThat(documents).singleElement()
            .satisfies(document -> assertThat(document.aclEntries()).singleElement()
                .satisfies(acl -> assertThat(acl.principalKey()).isEqualTo(PrincipalKeys.workspace(workspaceId))));
    }

    @Test
    void fetchChangesEmitsDatabaseRowsWithPropertiesBodyAclAndMetadata() {
        UUID workspaceId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        DataSourceEntity dataSource = dataSource(workspaceId, ownerId, DataSourceVisibility.PRIVATE);
        dataSource.putConfig(NotionPageConnector.DATABASE_ID_CONFIG_KEY, "database-1");
        FakeNotionClient notion = new FakeNotionClient();
        notion.database("database-1", "Roadmap", "https://notion.so/database-1",
            new NotionApiClient.NotionDataSource("data-source-1", "Roadmap table"));
        notion.page("row-1", "First task", "https://notion.so/row-1", "data_source_id", "data-source-1",
            properties("Status", "Done", "Priority", "High"));
        notion.page("row-child", "Nested detail", "https://notion.so/row-child", "page_id", "row-1", Map.of());
        notion.page("row-2", "Second task", "https://notion.so/row-2", "data_source_id", "data-source-1",
            properties("Done", "true"));
        notion.queryPages("data-source-1", "row-1", "row-2");
        notion.blocks("row-1",
            block("row-1-block", "paragraph", "Implement database ingestion", false),
            block("row-child", "child_page", "Nested detail", false)
        );
        notion.blocks("row-child",
            block("row-child-block", "paragraph", "Nested body", false)
        );
        notion.blocks("row-2");
        NotionPageConnector connector = new NotionPageConnector(notion);
        List<ConnectorDocumentEvent> events = new ArrayList<>();

        connector.fetchChanges(
            snapshot(dataSource, new SyncCursor(Map.of())),
            recordingSink(events)
        );
        List<RawExternalDocument> documents = events.stream().map(ConnectorDocumentEvent::document).toList();

        assertThat(documents)
            .extracting(RawExternalDocument::externalId)
            .containsExactly("row-1", "row-2", "row-child");
        assertThat(events)
            .extracting(event -> event.reference().qualifiedExternalId())
            .containsExactly("page:row-1", "page:row-2", "page:row-child");
        assertThat(events)
            .extracting(event -> event.reference().path())
            .containsExactly(
                List.of("Roadmap", "First task"),
                List.of("Roadmap", "Second task"),
                List.of("Roadmap", "First task", "Nested detail")
            );

        RawExternalDocument first = documents.get(0);
        assertThat(first.title()).isEqualTo("First task");
        assertThat(first.uri()).isEqualTo("https://notion.so/row-1");
        assertThat(first.content().text()).isEqualTo("""
            First task
            Status: Done
            Priority: High
            Implement database ingestion
            Nested detail
            """.stripTrailing());
        assertThat(first.metadata())
            .containsEntry("notionPageId", "row-1")
            .containsEntry("notionDatabaseId", "database-1")
            .containsEntry("notionDatabaseTitle", "Roadmap")
            .containsEntry("notionDataSourceId", "data-source-1")
            .containsEntry("notionDataSourceName", "Roadmap table")
            .containsEntry("notionDepth", 1)
            .containsEntry("notionPath", List.of("Roadmap", "First task"))
            .containsEntry("notionApiParentType", "data_source_id")
            .containsEntry("notionApiParentId", "data-source-1")
            .containsEntry("notionProperties", Map.of("Status", "Done", "Priority", "High"));
        assertThat(first.aclEntries()).singleElement()
            .satisfies(acl -> assertThat(acl.principalKey()).isEqualTo(PrincipalKeys.user(ownerId)));

        RawExternalDocument nested = documents.get(2);
        assertThat(nested.title()).isEqualTo("Nested detail");
        assertThat(nested.content().text()).isEqualTo("""
            Nested detail
            Nested body
            """.stripTrailing());
        assertThat(nested.metadata())
            .containsEntry("notionPageId", "row-child")
            .containsEntry("notionDatabaseId", "database-1")
            .containsEntry("notionDatabaseTitle", "Roadmap")
            .containsEntry("notionDataSourceId", "data-source-1")
            .containsEntry("notionDataSourceName", "Roadmap table")
            .containsEntry("notionParentPageId", "row-1")
            .containsEntry("notionParentTitle", "First task")
            .containsEntry("notionDepth", 2)
            .containsEntry("notionPath", List.of("Roadmap", "First task", "Nested detail"))
            .containsEntry("notionApiParentType", "page_id")
            .containsEntry("notionApiParentId", "row-1");
        assertThat(nested.metadata()).doesNotContainKey("notionRootPageId");

        RawExternalDocument second = documents.get(1);
        assertThat(second.content().text()).isEqualTo("""
            Second task
            Done: true
            """.stripTrailing());
        assertThat(second.metadata())
            .containsEntry("notionPath", List.of("Roadmap", "Second task"))
            .containsEntry("notionProperties", Map.of("Done", "true"));
    }

    @Test
    void databaseBatchEmitsAllRowDocumentsBeforeDeferredNestedWorkWithoutRetrievingRows() {
        FakeNotionClient notion = new FakeNotionClient();
        notion.page("root", "Root", "https://notion.so/root", "workspace", null, Map.of());
        notion.page("row-1", "First row", "https://notion.so/row-1");
        notion.page("row-2", "Second row", "https://notion.so/row-2");
        notion.page("row-child", "Row child", "https://notion.so/row-child");
        notion.page("nested-row", "Nested row", "https://notion.so/nested-row");
        notion.blocks("root", block("database-1", "child_database", "Roadmap", false));
        notion.blocks("row-1",
            block("row-child", "child_page", "Row child", false),
            block("database-2", "child_database", "Subtasks", false)
        );
        notion.blocks("row-2");
        notion.blocks("row-child");
        notion.blocks("nested-row");
        notion.database(
            "database-1", "Roadmap", "https://notion.so/database-1",
            new NotionApiClient.NotionDataSource("data-source-1", "Roadmap")
        );
        notion.database(
            "database-2", "Subtasks", "https://notion.so/database-2",
            new NotionApiClient.NotionDataSource("data-source-2", "Subtasks")
        );
        notion.queryPages("data-source-1", "row-1", "row-2");
        notion.queryPages("data-source-2", "nested-row");
        notion.failPage("row-1", new NotionApiException(429, "rate_limited"));
        notion.failPage("row-2", new NotionApiException(429, "rate_limited"));
        notion.failPage("nested-row", new NotionApiException(429, "rate_limited"));
        DataSourceEntity dataSource = dataSource(
            UUID.randomUUID(), UUID.randomUUID(), DataSourceVisibility.PRIVATE
        );
        dataSource.putConfig(NotionPageConnector.ROOT_PAGE_ID_CONFIG_KEY, "root");
        RecordingSink sink = new RecordingSink();

        new NotionPageConnector(notion).fetchChanges(
            snapshot(dataSource, new SyncCursor(Map.of())), sink
        );

        assertThat(sink.documents)
            .extracting(event -> event.document().externalId())
            .containsExactly("root", "row-1", "row-2", "row-child", "nested-row");
        assertThat(notion.retrievePageCalls("row-1")).isZero();
        assertThat(notion.retrievePageCalls("row-2")).isZero();
        assertThat(notion.retrievePageCalls("row-child")).isOne();
        assertThat(notion.retrievePageCalls("nested-row")).isZero();
        assertThat(sink.failures).isEmpty();
    }

    @Test
    void fetchChangesDiscoversDatabasesUnderRootChildAndRowPages() {
        FakeNotionClient notion = new FakeNotionClient();
        notion.page("root", "Root", "https://notion.so/root");
        notion.page("child", "Child", "https://notion.so/child");
        notion.page("row-1", "First row", "https://notion.so/row-1");
        notion.page("row-2", "Second row", "https://notion.so/row-2");
        notion.blocks("root", block("child", "child_page", "Child", false));
        notion.blocks("child", block("database-1", "child_database", "Roadmap", false));
        notion.blocks("row-1", block("database-2", "child_database", "Subtasks", false));
        notion.blocks("row-2");
        notion.database(
            "database-1", "Roadmap", "https://notion.so/database-1",
            new NotionApiClient.NotionDataSource("data-source-1", "Roadmap")
        );
        notion.database(
            "database-2", "Subtasks", "https://notion.so/database-2",
            new NotionApiClient.NotionDataSource("data-source-2", "Subtasks")
        );
        notion.queryPages("data-source-1", "row-1");
        notion.queryPages("data-source-2", "row-2");
        DataSourceEntity dataSource = dataSource(
            UUID.randomUUID(), UUID.randomUUID(), DataSourceVisibility.PRIVATE
        );
        dataSource.putConfig(NotionPageConnector.ROOT_PAGE_ID_CONFIG_KEY, "root");
        RecordingSink sink = new RecordingSink();

        new NotionPageConnector(notion).fetchChanges(
            snapshot(dataSource, new SyncCursor(Map.of())), sink
        );

        assertThat(sink.documents)
            .extracting(event -> event.document().externalId())
            .containsExactly("root", "child", "row-1", "row-2");
        assertThat(sink.documents.get(2).reference().path())
            .containsExactly("Root", "Child", "Roadmap", "First row");
        assertThat(sink.documents.get(3).reference().path())
            .containsExactly("Root", "Child", "Roadmap", "First row", "Subtasks", "Second row");
        assertThat(sink.failures).isEmpty();
    }

    @Test
    void fetchChangesDeduplicatesPagesAndDatabasesAcrossAllDiscoveryPaths() {
        FakeNotionClient notion = new FakeNotionClient();
        notion.page("root", "Root", "https://notion.so/root");
        notion.page("child", "Child", "https://notion.so/child");
        notion.page("row-1", "First row", "https://notion.so/row-1");
        notion.blocks("root",
            block("child", "child_page", "Child", false),
            block("database-1", "child_database", "Roadmap", false)
        );
        notion.blocks("child", block("database-1", "child_database", "Roadmap", false));
        notion.blocks("row-1");
        notion.database(
            "database-1", "Roadmap", "https://notion.so/database-1",
            new NotionApiClient.NotionDataSource("data-source-1", "Roadmap")
        );
        notion.queryPages("data-source-1", "row-1", "row-1");
        DataSourceEntity dataSource = dataSource(
            UUID.randomUUID(), UUID.randomUUID(), DataSourceVisibility.PRIVATE
        );
        dataSource.putConfig(NotionPageConnector.ROOT_PAGE_ID_CONFIG_KEY, "root");
        RecordingSink sink = new RecordingSink();

        new NotionPageConnector(notion).fetchChanges(
            snapshot(dataSource, new SyncCursor(Map.of())), sink
        );

        assertThat(sink.documents)
            .extracting(event -> event.document().externalId())
            .containsExactly("root", "child", "row-1");
        assertThat(notion.retrievePageCalls("row-1")).isZero();
        assertThat(notion.retrieveDatabaseCalls("database-1")).isOne();
        assertThat(notion.queryCalls("data-source-1")).isOne();
        assertThat(sink.failures).isEmpty();
    }

    @Test
    void childDatabaseFailureEmitsFailureAndContinuesWithSiblingDatabase() {
        FakeNotionClient notion = new FakeNotionClient();
        notion.page("root", "Root", "https://notion.so/root");
        notion.page("good-row", "Visible row", "https://notion.so/good-row");
        notion.blocks("root",
            block("database-bad", "child_database", "Hidden DB", false),
            block("database-good", "child_database", "Visible DB", false)
        );
        notion.blocks("good-row");
        notion.failDatabase("database-bad", new NotionApiException(404, "object_not_found"));
        notion.database(
            "database-good", "Visible DB", "https://notion.so/database-good",
            new NotionApiClient.NotionDataSource("data-source-good", "Visible DB")
        );
        notion.queryPages("data-source-good", "good-row");
        DataSourceEntity dataSource = dataSource(
            UUID.randomUUID(), UUID.randomUUID(), DataSourceVisibility.PRIVATE
        );
        dataSource.putConfig(NotionPageConnector.ROOT_PAGE_ID_CONFIG_KEY, "root");
        RecordingSink sink = new RecordingSink();

        new NotionPageConnector(notion).fetchChanges(
            snapshot(dataSource, new SyncCursor(Map.of())), sink
        );

        assertThat(sink.documents)
            .extracting(event -> event.document().externalId())
            .containsExactly("root", "good-row");
        assertThat(sink.failures).singleElement().satisfies(failure -> {
            assertThat(failure.reference().type()).isEqualTo(ConnectorItemType.DATABASE);
            assertThat(failure.reference().qualifiedExternalId()).isEqualTo("database:database-bad");
            assertThat(failure.reference().displayPath()).isEqualTo("Root / Hidden DB");
            assertThat(failure.stage()).isEqualTo(ConnectorFailureStage.RETRIEVE);
            assertThat(failure.userSafeReason())
                .contains("원본 database를 integration에 공유하세요")
                .doesNotContain("token", "response body");
        });
    }

    @Test
    void queryFailureAfterFirstBatchKeepsRowsAndContinuesWithSiblingDatabase() {
        FakeNotionClient notion = new FakeNotionClient();
        notion.page("root", "Root", "https://notion.so/root");
        notion.page("row-1", "First row", "https://notion.so/row-1");
        notion.page("row-child", "Row child", "https://notion.so/row-child");
        notion.page("row-2", "Second row", "https://notion.so/row-2");
        notion.blocks("root",
            block("database-1", "child_database", "Roadmap", false),
            block("database-2", "child_database", "Archive", false)
        );
        notion.blocks("row-1", block("row-child", "child_page", "Row child", false));
        notion.blocks("row-child");
        notion.blocks("row-2");
        notion.database(
            "database-1", "Roadmap", "https://notion.so/database-1",
            new NotionApiClient.NotionDataSource("data-source-1", "Roadmap")
        );
        notion.database(
            "database-2", "Archive", "https://notion.so/database-2",
            new NotionApiClient.NotionDataSource("data-source-2", "Archive")
        );
        notion.queryPages("data-source-1", "row-1");
        notion.failQueryAfterBatches("data-source-1", new NotionApiException(404, "object_not_found"));
        notion.queryPages("data-source-2", "row-2");
        DataSourceEntity dataSource = dataSource(
            UUID.randomUUID(), UUID.randomUUID(), DataSourceVisibility.PRIVATE
        );
        dataSource.putConfig(NotionPageConnector.ROOT_PAGE_ID_CONFIG_KEY, "root");
        RecordingSink sink = new RecordingSink();

        new NotionPageConnector(notion).fetchChanges(
            snapshot(dataSource, new SyncCursor(Map.of())), sink
        );

        assertThat(sink.documents)
            .extracting(event -> event.document().externalId())
            .containsExactly("root", "row-1", "row-child", "row-2");
        assertThat(sink.order).containsExactly(
            "document:root",
            "document:row-1",
            "document:row-child",
            "failure:database:database-1",
            "document:row-2"
        );
        assertThat(sink.failures).singleElement().satisfies(failure -> {
            assertThat(failure.reference().qualifiedExternalId()).isEqualTo("database:database-1");
            assertThat(failure.reference().displayPath()).isEqualTo("Root / Roadmap");
            assertThat(failure.stage()).isEqualTo(ConnectorFailureStage.QUERY);
        });
    }

    @Test
    void rowBlockFailureEmitsPageFailureAndContinuesWithNextRow() {
        FakeNotionClient notion = new FakeNotionClient();
        notion.page("root", "Root", "https://notion.so/root");
        notion.page("row-1", "Broken row", "https://notion.so/row-1");
        notion.page("row-2", "Healthy row", "https://notion.so/row-2");
        notion.blocks("root", block("database-1", "child_database", "Roadmap", false));
        notion.failBlocks("row-1", new NotionApiException(403, "restricted_resource"));
        notion.blocks("row-2");
        notion.database(
            "database-1", "Roadmap", "https://notion.so/database-1",
            new NotionApiClient.NotionDataSource("data-source-1", "Roadmap")
        );
        notion.queryPages("data-source-1", "row-1", "row-2");
        DataSourceEntity dataSource = dataSource(
            UUID.randomUUID(), UUID.randomUUID(), DataSourceVisibility.PRIVATE
        );
        dataSource.putConfig(NotionPageConnector.ROOT_PAGE_ID_CONFIG_KEY, "root");
        RecordingSink sink = new RecordingSink();

        new NotionPageConnector(notion).fetchChanges(
            snapshot(dataSource, new SyncCursor(Map.of())), sink
        );

        assertThat(sink.documents)
            .extracting(event -> event.document().externalId())
            .containsExactly("root", "row-2");
        assertThat(sink.failures).singleElement().satisfies(failure -> {
            assertThat(failure.reference().qualifiedExternalId()).isEqualTo("page:row-1");
            assertThat(failure.reference().displayPath()).isEqualTo("Root / Roadmap / Broken row");
            assertThat(failure.stage()).isEqualTo(ConnectorFailureStage.LIST_BLOCKS);
        });
    }

    @Test
    void childPageFailureEmitsFailureAndContinuesWithSiblingPage() {
        FakeNotionClient notion = new FakeNotionClient();
        notion.page("root", "Root", "https://notion.so/root");
        notion.page("child-b", "Healthy child", "https://notion.so/child-b");
        notion.blocks("root",
            block("child-a", "child_page", "Hidden child", false),
            block("child-b", "child_page", "Healthy child", false)
        );
        notion.blocks("child-b");
        notion.failPage("child-a", new NotionApiException(404, "object_not_found"));
        DataSourceEntity dataSource = dataSource(
            UUID.randomUUID(), UUID.randomUUID(), DataSourceVisibility.PRIVATE
        );
        dataSource.putConfig(NotionPageConnector.ROOT_PAGE_ID_CONFIG_KEY, "root");
        RecordingSink sink = new RecordingSink();

        new NotionPageConnector(notion).fetchChanges(
            snapshot(dataSource, new SyncCursor(Map.of())), sink
        );

        assertThat(sink.documents)
            .extracting(event -> event.document().externalId())
            .containsExactly("root", "child-b");
        assertThat(sink.failures).singleElement().satisfies(failure -> {
            assertThat(failure.reference().qualifiedExternalId()).isEqualTo("page:child-a");
            assertThat(failure.reference().displayPath()).isEqualTo("Root / Hidden child");
            assertThat(failure.stage()).isEqualTo(ConnectorFailureStage.RETRIEVE);
        });
    }

    @Test
    void unsupportedDatabaseBlockEmitsBlockFailureWithoutFollowingLinkToPage() {
        FakeNotionClient notion = new FakeNotionClient();
        notion.page("root", "Root", "https://notion.so/root");
        notion.blocks("root",
            new NotionApiClient.NotionBlock(
                "unsupported-1", "unsupported", "Linked database", false, "child_database"
            ),
            block("link-1", "link_to_page", "External page", false)
        );
        DataSourceEntity dataSource = dataSource(
            UUID.randomUUID(), UUID.randomUUID(), DataSourceVisibility.PRIVATE
        );
        dataSource.putConfig(NotionPageConnector.ROOT_PAGE_ID_CONFIG_KEY, "root");
        RecordingSink sink = new RecordingSink();

        new NotionPageConnector(notion).fetchChanges(
            snapshot(dataSource, new SyncCursor(Map.of())), sink
        );

        assertThat(sink.documents)
            .extracting(event -> event.document().externalId())
            .containsExactly("root");
        assertThat(sink.failures).singleElement().satisfies(failure -> {
            assertThat(failure.reference().qualifiedExternalId()).isEqualTo("block:unsupported-1");
            assertThat(failure.reference().displayPath()).isEqualTo("Root / Linked database");
            assertThat(failure.stage()).isEqualTo(ConnectorFailureStage.RETRIEVE);
            assertThat(failure.userSafeReason()).contains("child_database");
        });
        assertThat(notion.retrievePageCalls("link-1")).isZero();
        assertThat(notion.retrieveDatabaseCalls("link-1")).isZero();
        assertThat(notion.retrievePageCalls("unsupported-1")).isZero();
        assertThat(notion.retrieveDatabaseCalls("unsupported-1")).isZero();
    }

    @Test
    void documentSinkInfrastructureFailurePropagates() {
        FakeNotionClient notion = new FakeNotionClient();
        notion.page("root", "Root", "https://notion.so/root");
        notion.blocks("root");
        DataSourceEntity dataSource = dataSource(
            UUID.randomUUID(), UUID.randomUUID(), DataSourceVisibility.PRIVATE
        );
        dataSource.putConfig(NotionPageConnector.ROOT_PAGE_ID_CONFIG_KEY, "root");
        ConnectorEventSink failingSink = new ConnectorEventSink() {
            @Override
            public void onDocument(ConnectorDocumentEvent event) {
                throw new IllegalStateException("document sink unavailable");
            }

            @Override
            public void onFailure(ConnectorFailureEvent event) {
                throw new AssertionError("failure event should not replace the infrastructure exception");
            }
        };

        assertThatThrownBy(() -> new NotionPageConnector(notion).fetchChanges(
            snapshot(dataSource, new SyncCursor(Map.of())), failingSink
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("document sink unavailable");
    }

    @Test
    void failureSinkInfrastructureFailurePropagates() {
        FakeNotionClient notion = new FakeNotionClient();
        notion.page("root", "Root", "https://notion.so/root");
        notion.blocks("root", block("database-bad", "child_database", "Hidden DB", false));
        notion.failDatabase("database-bad", new NotionApiException(404, "object_not_found"));
        DataSourceEntity dataSource = dataSource(
            UUID.randomUUID(), UUID.randomUUID(), DataSourceVisibility.PRIVATE
        );
        dataSource.putConfig(NotionPageConnector.ROOT_PAGE_ID_CONFIG_KEY, "root");
        ConnectorEventSink failingSink = new ConnectorEventSink() {
            @Override
            public void onDocument(ConnectorDocumentEvent event) {
            }

            @Override
            public void onFailure(ConnectorFailureEvent event) {
                throw new IllegalStateException("failure sink unavailable");
            }
        };

        assertThatThrownBy(() -> new NotionPageConnector(notion).fetchChanges(
            snapshot(dataSource, new SyncCursor(Map.of())), failingSink
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("failure sink unavailable");
    }

    @Test
    void fetchChangesReportsDatabaseWithMultipleDataSources() {
        DataSourceEntity dataSource = dataSource(UUID.randomUUID(), UUID.randomUUID(), DataSourceVisibility.PRIVATE);
        dataSource.putConfig(NotionPageConnector.DATABASE_ID_CONFIG_KEY, "database-1");
        FakeNotionClient notion = new FakeNotionClient();
        notion.database("database-1", "Roadmap", "https://notion.so/database-1",
            new NotionApiClient.NotionDataSource("data-source-1", "Main"),
            new NotionApiClient.NotionDataSource("data-source-2", "Archive"));
        NotionPageConnector connector = new NotionPageConnector(notion);
        RecordingSink sink = new RecordingSink();

        connector.fetchChanges(
            snapshot(dataSource, new SyncCursor(Map.of())),
            sink
        );

        assertThat(sink.documents).isEmpty();
        assertThat(sink.failures).singleElement().satisfies(failure -> {
            assertThat(failure.reference().qualifiedExternalId()).isEqualTo("database:database-1");
            assertThat(failure.stage()).isEqualTo(ConnectorFailureStage.RETRIEVE);
            assertThat(failure.userSafeReason()).contains("data source가 1개");
        });
    }

    @Test
    void fetchChangesRequiresRootPageId() {
        NotionPageConnector connector = new NotionPageConnector(new FakeNotionClient());
        DataSourceEntity dataSource = dataSource(UUID.randomUUID(), UUID.randomUUID(), DataSourceVisibility.PRIVATE);

        assertThatThrownBy(() -> connector.fetchChanges(
            snapshot(dataSource, new SyncCursor(Map.of())),
            documentSink(new ArrayList<>())
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(NotionPageConnector.ROOT_PAGE_ID_CONFIG_KEY);
    }

    @Test
    void fetchChangesTraversesMoreThanFifteenHundredChildPagesWithoutGrowingTheCallStack() {
        FakeNotionClient notion = new FakeNotionClient();
        for (int index = 0; index < DEEP_CHAIN_LENGTH; index++) {
            String pageId = "page-" + index;
            notion.page(
                pageId,
                "Page " + index,
                "https://notion.so/" + pageId,
                index == 0 ? "workspace" : "page_id",
                index == 0 ? null : "page-" + (index - 1),
                Map.of()
            );
            if (index + 1 < DEEP_CHAIN_LENGTH) {
                notion.blocks(pageId, block(
                    "page-" + (index + 1),
                    "child_page",
                    "Page " + (index + 1),
                    false
                ));
            } else {
                notion.blocks(pageId);
            }
        }
        DataSourceEntity dataSource = dataSource(
            UUID.randomUUID(), UUID.randomUUID(), DataSourceVisibility.PRIVATE
        );
        dataSource.putConfig(NotionPageConnector.ROOT_PAGE_ID_CONFIG_KEY, "page-0");
        CountingPageSink sink = new CountingPageSink();

        new NotionPageConnector(notion).fetchChanges(
            snapshot(dataSource, new SyncCursor(Map.of())), sink
        );

        assertThat(sink.count).isEqualTo(DEEP_CHAIN_LENGTH);
    }

    @Test
    void fetchChangesCollectsMoreThanFifteenHundredNestedBlocksWithoutGrowingTheCallStack() {
        FakeNotionClient notion = new FakeNotionClient();
        notion.page("root", "Root", "https://notion.so/root", "workspace", null, Map.of());
        notion.blocks("root", block("block-0", "paragraph", "Line 0", true));
        for (int index = 0; index < DEEP_CHAIN_LENGTH; index++) {
            if (index + 1 < DEEP_CHAIN_LENGTH) {
                notion.blocks("block-" + index, block(
                    "block-" + (index + 1),
                    "paragraph",
                    "Line " + (index + 1),
                    true
                ));
            } else {
                notion.blocks("block-" + index);
            }
        }
        DataSourceEntity dataSource = dataSource(
            UUID.randomUUID(), UUID.randomUUID(), DataSourceVisibility.PRIVATE
        );
        dataSource.putConfig(NotionPageConnector.ROOT_PAGE_ID_CONFIG_KEY, "root");
        List<RawExternalDocument> documents = new ArrayList<>();

        new NotionPageConnector(notion).fetchChanges(
            snapshot(dataSource, new SyncCursor(Map.of())), documentSink(documents)
        );

        assertThat(documents).singleElement().satisfies(document -> {
            assertThat(document.content().text().lines()).hasSize(DEEP_CHAIN_LENGTH + 1);
            assertThat(document.content().text()).startsWith("Root\nLine 0\nLine 1");
            assertThat(document.content().text()).endsWith("Line " + (DEEP_CHAIN_LENGTH - 1));
        });
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

    private static DataSourceSnapshot snapshot(DataSourceEntity dataSource, SyncCursor cursor) {
        return new DataSourceSnapshot(
            dataSource.getId(),
            dataSource.getWorkspaceId(),
            dataSource.getOwnerUserId(),
            dataSource.getType(),
            dataSource.getVisibility(),
            dataSource.configValues(),
            cursor
        );
    }

    private static ConnectorEventSink documentSink(List<RawExternalDocument> documents) {
        return new ConnectorEventSink() {
            @Override
            public void onDocument(ConnectorDocumentEvent event) {
                documents.add(event.document());
            }

            @Override
            public void onFailure(ConnectorFailureEvent event) {
                throw new AssertionError("예상하지 않은 connector 실패 event: " + event);
            }
        };
    }

    private static ConnectorEventSink recordingSink(List<ConnectorDocumentEvent> events) {
        return new ConnectorEventSink() {
            @Override
            public void onDocument(ConnectorDocumentEvent event) {
                events.add(event);
            }

            @Override
            public void onFailure(ConnectorFailureEvent event) {
                throw new AssertionError("예상하지 않은 connector 실패 event: " + event);
            }
        };
    }

    private static final class RecordingSink implements ConnectorEventSink {
        private final List<ConnectorDocumentEvent> documents = new ArrayList<>();
        private final List<ConnectorFailureEvent> failures = new ArrayList<>();
        private final List<String> order = new ArrayList<>();

        @Override
        public void onDocument(ConnectorDocumentEvent event) {
            documents.add(event);
            order.add("document:" + event.document().externalId());
        }

        @Override
        public void onFailure(ConnectorFailureEvent event) {
            failures.add(event);
            order.add("failure:" + event.reference().qualifiedExternalId());
        }
    }

    private static final class CountingPageSink implements ConnectorEventSink {
        private int count;

        @Override
        public void onDocument(ConnectorDocumentEvent event) {
            assertThat(event.document().externalId()).isEqualTo("page-" + count);
            count++;
        }

        @Override
        public void onFailure(ConnectorFailureEvent event) {
            throw new AssertionError("예상하지 않은 connector 실패 event: " + event);
        }
    }

    private static NotionApiClient.NotionBlock block(
        String id,
        String type,
        String plainText,
        boolean hasChildren
    ) {
        return new NotionApiClient.NotionBlock(id, type, plainText, hasChildren, null);
    }

    private static Map<String, String> properties(String... keyValues) {
        Map<String, String> properties = new java.util.LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            properties.put(keyValues[index], keyValues[index + 1]);
        }
        return properties;
    }

    private static class FakeNotionClient implements NotionClient {
        private final Map<String, NotionApiClient.NotionPage> pages = new java.util.LinkedHashMap<>();
        private final Map<String, NotionApiClient.NotionDatabase> databases = new java.util.LinkedHashMap<>();
        private final Map<String, List<List<String>>> dataSourcePageBatches = new java.util.LinkedHashMap<>();
        private final Map<String, List<NotionApiClient.NotionBlock>> blockChildren = new java.util.LinkedHashMap<>();
        private final Map<String, NotionApiException> pageFailures = new java.util.LinkedHashMap<>();
        private final Map<String, NotionApiException> databaseFailures = new java.util.LinkedHashMap<>();
        private final Map<String, NotionApiException> queryFailures = new java.util.LinkedHashMap<>();
        private final Map<String, NotionApiException> blockFailures = new java.util.LinkedHashMap<>();
        private final Map<String, Integer> retrievePageCalls = new java.util.LinkedHashMap<>();
        private final Map<String, Integer> retrieveDatabaseCalls = new java.util.LinkedHashMap<>();
        private final Map<String, Integer> queryCalls = new java.util.LinkedHashMap<>();

        void page(String id, String title, String url) {
            page(id, title, url, "root-page".equals(id) ? "workspace" : "page_id",
                "root-page".equals(id) ? null : "root-page", Map.of());
        }

        void page(String id, String title, String url, String parentType, String parentId, Map<String, String> properties) {
            pages.put(id, new NotionApiClient.NotionPage(
                id,
                title,
                url,
                "https://public.notion.site/" + id,
                Instant.parse("2026-06-01T00:00:00Z"),
                Instant.parse("2026-06-02T00:00:00Z"),
                "creator-" + id,
                "editor-" + id,
                parentType,
                parentId,
                false,
                properties
            ));
        }

        void database(String id, String title, String url, NotionApiClient.NotionDataSource... dataSources) {
            databases.put(id, new NotionApiClient.NotionDatabase(id, title, url, List.of(dataSources)));
        }

        void queryPages(String dataSourceId, String... pageIds) {
            dataSourcePageBatches.put(dataSourceId, List.of(List.of(pageIds)));
        }

        void blocks(String id, NotionApiClient.NotionBlock... blocks) {
            blockChildren.put(id, List.of(blocks));
        }

        void failPage(String pageId, NotionApiException failure) {
            pageFailures.put(pageId, failure);
        }

        void failDatabase(String databaseId, NotionApiException failure) {
            databaseFailures.put(databaseId, failure);
        }

        void failQueryAfterBatches(String dataSourceId, NotionApiException failure) {
            queryFailures.put(dataSourceId, failure);
        }

        void failBlocks(String blockId, NotionApiException failure) {
            blockFailures.put(blockId, failure);
        }

        int retrievePageCalls(String pageId) {
            return retrievePageCalls.getOrDefault(pageId, 0);
        }

        int retrieveDatabaseCalls(String databaseId) {
            return retrieveDatabaseCalls.getOrDefault(databaseId, 0);
        }

        int queryCalls(String dataSourceId) {
            return queryCalls.getOrDefault(dataSourceId, 0);
        }

        @Override
        public NotionApiClient.NotionPage retrievePage(String pageId) {
            retrievePageCalls.merge(pageId, 1, Integer::sum);
            NotionApiException failure = pageFailures.get(pageId);
            if (failure != null) {
                throw failure;
            }
            return pages.get(pageId);
        }

        @Override
        public NotionApiClient.NotionDatabase retrieveDatabase(String databaseId) {
            retrieveDatabaseCalls.merge(databaseId, 1, Integer::sum);
            NotionApiException failure = databaseFailures.get(databaseId);
            if (failure != null) {
                throw failure;
            }
            return databases.get(databaseId);
        }

        @Override
        public Batch<NotionApiClient.NotionPage> queryDataSourcePages(
            String dataSourceId,
            String startCursor
        ) {
            queryCalls.merge(dataSourceId, 1, Integer::sum);
            List<List<String>> batches = dataSourcePageBatches.getOrDefault(dataSourceId, List.of());
            int batchIndex = startCursor == null ? 0 : Integer.parseInt(startCursor.substring("batch-".length()));
            if (batchIndex >= batches.size()) {
                NotionApiException failure = queryFailures.get(dataSourceId);
                if (failure != null) {
                    throw failure;
                }
                return new Batch<>(List.of(), null);
            }
            List<NotionApiClient.NotionPage> items = batches.get(batchIndex).stream().map(pages::get).toList();
            boolean hasNext = batchIndex + 1 < batches.size() || queryFailures.containsKey(dataSourceId);
            return new Batch<>(items, hasNext ? "batch-" + (batchIndex + 1) : null);
        }

        @Override
        public Batch<NotionApiClient.NotionBlock> listBlockChildren(String blockId, String startCursor) {
            NotionApiException failure = blockFailures.get(blockId);
            if (failure != null) {
                throw failure;
            }
            return new Batch<>(blockChildren.getOrDefault(blockId, List.of()), null);
        }
    }
}
