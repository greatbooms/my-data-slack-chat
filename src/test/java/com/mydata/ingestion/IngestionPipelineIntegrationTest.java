package com.mydata.ingestion;

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
}
