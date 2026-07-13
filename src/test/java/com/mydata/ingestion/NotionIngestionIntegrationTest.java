package com.mydata.ingestion;

import com.mydata.auth.PrincipalKeys;
import com.mydata.auth.Permission;
import com.mydata.connectors.notion.NotionApiClient;
import com.mydata.connectors.notion.NotionApiException;
import com.mydata.connectors.notion.NotionClient;
import com.mydata.connectors.notion.NotionPageConnector;
import com.mydata.datasources.DataSourceEntity;
import com.mydata.datasources.DataSourceRepository;
import com.mydata.datasources.DataSourceStatus;
import com.mydata.datasources.DataSourceType;
import com.mydata.datasources.SyncMode;
import com.mydata.documents.DocumentAclEntryRepository;
import com.mydata.documents.DocumentChunkRepository;
import com.mydata.documents.ExternalDocumentEntity;
import com.mydata.documents.ExternalDocumentRepository;
import com.mydata.support.PostgresIntegrationTest;
import com.mydata.users.UserEntity;
import com.mydata.users.UserRepository;
import com.mydata.workspaces.WorkspaceEntity;
import com.mydata.workspaces.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NotionIngestionIntegrationTest extends PostgresIntegrationTest {
    @Autowired UserRepository users;
    @Autowired WorkspaceRepository workspaces;
    @Autowired DataSourceRepository dataSources;
    @Autowired IngestionJobRepository ingestionJobs;
    @Autowired IngestionJobItemRepository jobItems;
    @Autowired IngestionWorker worker;
    @Autowired ExternalDocumentRepository documents;
    @Autowired DocumentAclEntryRepository aclEntries;
    @Autowired DocumentChunkRepository chunks;
    @Autowired FakeNotionClient notion;

    @BeforeEach
    void resetNotion() {
        notion.reset();
    }

    @Test
    void workerIngestsNotionRootPageIntoDocumentsChunksAndAcl() {
        UserEntity user = users.save(UserEntity.create("notion-ingestion-owner@example.com", "Notion Owner"));
        WorkspaceEntity workspace = workspaces.save(WorkspaceEntity.create(user.getId(), "Notion workspace"));
        DataSourceEntity dataSource = DataSourceEntity.create(
            workspace.getId(),
            DataSourceType.NOTION,
            "Notion wiki",
            DataSourceStatus.ACTIVE,
            SyncMode.MANUAL
        );
        dataSource.assignOwner(user.getId());
        dataSource.putConfig(NotionPageConnector.ROOT_PAGE_ID_CONFIG_KEY, "root-page");
        dataSource = dataSources.saveAndFlush(dataSource);
        notion.page("root-page", "Root Plan", "https://notion.so/root-page");
        notion.blocks("root-page",
            new NotionApiClient.NotionBlock("block-1", "heading_1", "Intro", false, null),
            new NotionApiClient.NotionBlock("block-2", "paragraph", "alpha beta gamma", false, null)
        );
        IngestionJobEntity job = ingestionJobs.saveAndFlush(IngestionJobEntity.pending(
            workspace.getId(),
            dataSource.getId(),
            IngestionTriggerType.MANUAL,
            user.getId()
        ));

        worker.run(job.getId());

        ExternalDocumentEntity document = documents
            .findByDataSourceIdAndExternalId(dataSource.getId(), "root-page")
            .orElseThrow();
        assertThat(document.getTitle()).isEqualTo("Root Plan");
        assertThat(document.getSourceType()).isEqualTo(DataSourceType.NOTION.name());
        assertThat(document.getUri()).isEqualTo("https://notion.so/root-page");
        assertThat(chunks.findByDocumentIdOrderByChunkIndex(document.getId()))
            .hasSize(1)
            .first()
            .satisfies(chunk -> assertThat(chunk.getContent()).isEqualTo("Root Plan Intro alpha beta gamma"));
        assertThat(aclEntries.findByDocumentId(document.getId()))
            .hasSize(1)
            .first()
            .satisfies(acl -> {
                assertThat(acl.getPrincipalKey()).isEqualTo(PrincipalKeys.user(user.getId()));
                assertThat(acl.getPermission()).isEqualTo(Permission.READ);
                assertThat(acl.getSource()).isEqualTo("NOTION");
                assertThat(acl.isInherited()).isFalse();
            });
        assertThat(ingestionJobs.findById(job.getId()).orElseThrow().getStatus())
            .isEqualTo(IngestionJobStatus.SUCCEEDED);
    }

    @Test
    void workerKeepsNestedDatabaseRowsWhenSiblingDatabaseFails() {
        UserEntity user = users.save(UserEntity.create(
            "notion-partial-failure-owner@example.com", "Notion Partial Owner"
        ));
        WorkspaceEntity workspace = workspaces.save(WorkspaceEntity.create(
            user.getId(), "Notion partial workspace"
        ));
        DataSourceEntity dataSource = DataSourceEntity.create(
            workspace.getId(),
            DataSourceType.NOTION,
            "Notion partial wiki",
            DataSourceStatus.ACTIVE,
            SyncMode.MANUAL
        );
        dataSource.assignOwner(user.getId());
        dataSource.putConfig(NotionPageConnector.ROOT_PAGE_ID_CONFIG_KEY, "root");
        dataSource = dataSources.saveAndFlush(dataSource);
        String principalKey = PrincipalKeys.user(user.getId());
        notion.page("root", "Root", "https://notion.so/root");
        notion.page("good-row", "Good row", "https://notion.so/good-row");
        notion.blocks("root",
            new NotionApiClient.NotionBlock(
                "database-bad", "child_database", "Hidden DB", false, null
            ),
            new NotionApiClient.NotionBlock(
                "database-good", "child_database", "Visible DB", false, null
            )
        );
        notion.blocks("good-row");
        notion.failDatabase("database-bad", new NotionApiException(404, "object_not_found"));
        notion.database(
            "database-good", "Visible DB", "https://notion.so/database-good",
            new NotionApiClient.NotionDataSource("data-source-good", "Visible DB")
        );
        notion.queryPages("data-source-good", "good-row");
        IngestionJobEntity job = ingestionJobs.saveAndFlush(IngestionJobEntity.pending(
            workspace.getId(),
            dataSource.getId(),
            IngestionTriggerType.MANUAL,
            user.getId()
        ));

        worker.run(job.getId());

        assertThat(documents.findByDataSourceIdAndExternalId(dataSource.getId(), "root")).isPresent();
        ExternalDocumentEntity goodRow = documents
            .findByDataSourceIdAndExternalId(dataSource.getId(), "good-row")
            .orElseThrow();
        assertThat(ingestionJobs.findById(job.getId()).orElseThrow().getStatus())
            .isEqualTo(IngestionJobStatus.PARTIAL_FAILED);
        assertThat(jobItems.findByJobIdOrderByProcessedAtAscIdAsc(job.getId()))
            .extracting(IngestionJobItemEntity::getStatus)
            .containsExactlyInAnyOrder(
                IngestionJobItemStatus.SUCCEEDED,
                IngestionJobItemStatus.SUCCEEDED,
                IngestionJobItemStatus.FAILED
            );
        assertThat(jobItems.findByJobIdOrderByProcessedAtAscIdAsc(job.getId()))
            .filteredOn(item -> item.getStatus() == IngestionJobItemStatus.FAILED)
            .singleElement()
            .satisfies(item -> {
                assertThat(item.getExternalId()).isEqualTo("database:database-bad");
                assertThat(item.getReason())
                    .contains("[DATABASE] Root / Hidden DB (RETRIEVE)")
                    .contains("원본 database를 integration에 공유하세요");
            });
        assertThat(aclEntries.findByDocumentId(goodRow.getId()))
            .singleElement()
            .satisfies(acl -> assertThat(acl.getPrincipalKey()).isEqualTo(principalKey));
    }

    @TestConfiguration
    static class FakeNotionConfiguration {
        @Bean
        @Primary
        FakeNotionClient fakeNotionClient() {
            return new FakeNotionClient();
        }
    }

    static class FakeNotionClient implements NotionClient {
        private final Map<String, NotionApiClient.NotionPage> pages = new LinkedHashMap<>();
        private final Map<String, NotionApiClient.NotionDatabase> databases = new LinkedHashMap<>();
        private final Map<String, List<String>> dataSourcePages = new LinkedHashMap<>();
        private final Map<String, List<NotionApiClient.NotionBlock>> blockChildren = new LinkedHashMap<>();
        private final Map<String, NotionApiException> databaseFailures = new LinkedHashMap<>();

        void reset() {
            pages.clear();
            databases.clear();
            dataSourcePages.clear();
            blockChildren.clear();
            databaseFailures.clear();
        }

        void page(String id, String title, String url) {
            pages.put(id, new NotionApiClient.NotionPage(
                id,
                title,
                url,
                Instant.parse("2026-06-01T00:00:00Z"),
                Instant.parse("2026-06-02T00:00:00Z")
            ));
        }

        void blocks(String id, NotionApiClient.NotionBlock... blocks) {
            blockChildren.put(id, List.of(blocks));
        }

        void database(String id, String title, String url, NotionApiClient.NotionDataSource... dataSources) {
            databases.put(id, new NotionApiClient.NotionDatabase(id, title, url, List.of(dataSources)));
        }

        void queryPages(String dataSourceId, String... pageIds) {
            dataSourcePages.put(dataSourceId, List.of(pageIds));
        }

        void failDatabase(String databaseId, NotionApiException failure) {
            databaseFailures.put(databaseId, failure);
        }

        @Override
        public NotionApiClient.NotionPage retrievePage(String pageId) {
            return pages.get(pageId);
        }

        @Override
        public NotionApiClient.NotionDatabase retrieveDatabase(String databaseId) {
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
            return new Batch<>(dataSourcePages.getOrDefault(dataSourceId, List.of()).stream()
                .map(pages::get)
                .toList(), null);
        }

        @Override
        public Batch<NotionApiClient.NotionBlock> listBlockChildren(String blockId, String startCursor) {
            return new Batch<>(blockChildren.getOrDefault(blockId, List.of()), null);
        }
    }
}
