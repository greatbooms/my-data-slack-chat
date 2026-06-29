package com.mydata.ingestion;

import com.mydata.auth.PrincipalKeys;
import com.mydata.auth.Permission;
import com.mydata.connectors.notion.NotionApiClient;
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
            new NotionApiClient.NotionBlock("block-1", "heading_1", "Intro", false),
            new NotionApiClient.NotionBlock("block-2", "paragraph", "alpha beta gamma", false)
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
        private final Map<String, List<NotionApiClient.NotionBlock>> blockChildren = new LinkedHashMap<>();

        void reset() {
            pages.clear();
            blockChildren.clear();
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
