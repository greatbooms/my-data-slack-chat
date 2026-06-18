package com.mydata.ingestion;

import com.mydata.connectors.core.DataSourceConnector;
import com.mydata.connectors.core.DocumentHandler;
import com.mydata.connectors.core.RawAclEntry;
import com.mydata.connectors.core.RawContent;
import com.mydata.connectors.core.RawExternalDocument;
import com.mydata.connectors.core.SyncCursor;
import com.mydata.auth.PrincipalKeys;
import com.mydata.auth.Permission;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IngestionPipelineIntegrationTest extends PostgresIntegrationTest {
    @Autowired UserRepository users;
    @Autowired WorkspaceRepository workspaces;
    @Autowired DataSourceRepository dataSources;
    @Autowired IngestionJobRepository ingestionJobs;
    @Autowired IngestionWorker worker;
    @Autowired ExternalDocumentRepository documents;
    @Autowired DocumentAclEntryRepository aclEntries;
    @Autowired DocumentChunkRepository chunks;

    @Test
    void workerIngestsLocalTextDataSource() {
        UserEntity user = users.save(UserEntity.create("local-owner@example.com", "Local Owner"));
        WorkspaceEntity workspace = workspaces.save(WorkspaceEntity.create(user.getId(), "Local workspace"));
        DataSourceEntity dataSource = dataSources.save(DataSourceEntity.create(
            workspace.getId(),
            DataSourceType.LOCAL_TEXT,
            "Local notes",
            DataSourceStatus.ACTIVE,
            SyncMode.MANUAL
        ));
        dataSource.putConfig("externalId", "note-1");
        dataSource.putConfig("title", "Local note");
        dataSource.putConfig("content", "alpha beta gamma delta epsilon zeta eta theta");
        dataSource.putConfig("principalKey", PrincipalKeys.user(user.getId()));
        dataSource = dataSources.saveAndFlush(dataSource);
        IngestionJobEntity job = ingestionJobs.saveAndFlush(IngestionJobEntity.pending(
            workspace.getId(),
            dataSource.getId(),
            IngestionTriggerType.MANUAL,
            user.getId()
        ));

        worker.run(job.getId());

        IngestionJobEntity reloadedJob = ingestionJobs.findById(job.getId()).orElseThrow();
        ExternalDocumentEntity document = documents
            .findByDataSourceIdAndExternalId(dataSource.getId(), "note-1")
            .orElseThrow();

        assertThat(document.getTitle()).isEqualTo("Local note");
        assertThat(document.getContentHash()).isNotBlank();
        assertThat(aclEntries.findByDocumentId(document.getId()))
            .hasSize(1)
            .first()
            .satisfies(acl -> {
                assertThat(acl.getPrincipalKey()).isEqualTo(PrincipalKeys.user(user.getId()));
                assertThat(acl.getPermission()).isEqualTo(Permission.READ);
                assertThat(acl.getSource()).isEqualTo("MANUAL");
                assertThat(acl.isInherited()).isFalse();
            });
        assertThat(chunks.findByDocumentIdOrderByChunkIndex(document.getId()))
            .hasSize(1)
            .first()
            .satisfies(chunk -> {
                assertThat(chunk.getContent()).isEqualTo("alpha beta gamma delta epsilon zeta eta theta");
                assertThat(chunk.getTokenCount()).isEqualTo(8);
            });
        assertThat(reloadedJob.getStatus()).isEqualTo(IngestionJobStatus.SUCCEEDED);
    }

    @Test
    void failedPipelineRollsBackDocumentWritesButMarksJobFailed() {
        UserEntity user = users.save(UserEntity.create("notion-owner@example.com", "Notion Owner"));
        WorkspaceEntity workspace = workspaces.save(WorkspaceEntity.create(user.getId(), "Notion workspace"));
        DataSourceEntity dataSource = dataSources.saveAndFlush(DataSourceEntity.create(
            workspace.getId(),
            DataSourceType.NOTION,
            "Notion notes",
            DataSourceStatus.ACTIVE,
            SyncMode.MANUAL
        ));
        IngestionJobEntity job = ingestionJobs.saveAndFlush(IngestionJobEntity.pending(
            workspace.getId(),
            dataSource.getId(),
            IngestionTriggerType.MANUAL,
            user.getId()
        ));

        worker.run(job.getId());

        assertThat(ingestionJobs.findById(job.getId()).orElseThrow().getStatus())
            .isEqualTo(IngestionJobStatus.FAILED);
        assertThat(documents.findByDataSourceIdAndExternalId(dataSource.getId(), "bad-note"))
            .isEmpty();
    }

    @Test
    void sameContentTitleAndAclChangesAreRefreshed() {
        UserEntity firstUser = users.save(UserEntity.create("first-local-owner@example.com", "First Owner"));
        UserEntity secondUser = users.save(UserEntity.create("second-local-owner@example.com", "Second Owner"));
        WorkspaceEntity workspace = workspaces.save(WorkspaceEntity.create(firstUser.getId(), "Shared local workspace"));
        DataSourceEntity dataSource = dataSources.save(DataSourceEntity.create(
            workspace.getId(),
            DataSourceType.LOCAL_TEXT,
            "Local notes",
            DataSourceStatus.ACTIVE,
            SyncMode.MANUAL
        ));
        dataSource.putConfig("externalId", "same-note");
        dataSource.putConfig("title", "First title");
        dataSource.putConfig("content", "same content");
        dataSource.putConfig("principalKey", PrincipalKeys.user(firstUser.getId()));
        dataSource = dataSources.saveAndFlush(dataSource);
        IngestionJobEntity firstJob = ingestionJobs.saveAndFlush(IngestionJobEntity.pending(
            workspace.getId(),
            dataSource.getId(),
            IngestionTriggerType.MANUAL,
            firstUser.getId()
        ));
        worker.run(firstJob.getId());

        dataSource.putConfig("title", "Second title");
        dataSource.putConfig("principalKey", PrincipalKeys.user(secondUser.getId()));
        dataSource = dataSources.saveAndFlush(dataSource);
        IngestionJobEntity secondJob = ingestionJobs.saveAndFlush(IngestionJobEntity.pending(
            workspace.getId(),
            dataSource.getId(),
            IngestionTriggerType.MANUAL,
            secondUser.getId()
        ));

        worker.run(secondJob.getId());

        ExternalDocumentEntity document = documents
            .findByDataSourceIdAndExternalId(dataSource.getId(), "same-note")
            .orElseThrow();
        assertThat(document.getTitle()).isEqualTo("Second title");
        assertThat(aclEntries.findByDocumentId(document.getId()))
            .hasSize(1)
            .first()
            .satisfies(acl -> assertThat(acl.getPrincipalKey()).isEqualTo(PrincipalKeys.user(secondUser.getId())));
        assertThat(ingestionJobs.findById(secondJob.getId()).orElseThrow().getStatus())
            .isEqualTo(IngestionJobStatus.SUCCEEDED);
    }

    @TestConfiguration
    static class FailingNotionConnectorConfiguration {
        @Bean
        DataSourceConnector failingNotionConnector() {
            return new DataSourceConnector() {
                @Override
                public DataSourceType supports() {
                    return DataSourceType.NOTION;
                }

                @Override
                public SyncCursor fetchChanges(DataSourceEntity dataSource, SyncCursor cursor, DocumentHandler handler) {
                    handler.handle(new RawExternalDocument(
                        "bad-note",
                        DataSourceType.NOTION,
                        "Bad note",
                        null,
                        "text/plain",
                        null,
                        null,
                        "bad-hash",
                        Map.of(),
                        new RawContent("bad content", "text/plain"),
                        List.of(new RawAclEntry(PrincipalKeys.user(dataSource.getWorkspaceId()), "WRITE", false, "TEST"))
                    ));
                    return cursor;
                }
            };
        }
    }
}
