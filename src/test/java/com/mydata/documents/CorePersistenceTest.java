package com.mydata.documents;

import com.mydata.auth.Permission;
import com.mydata.auth.PrincipalKeys;
import com.mydata.datasources.DataSourceEntity;
import com.mydata.datasources.DataSourceRepository;
import com.mydata.datasources.DataSourceStatus;
import com.mydata.datasources.DataSourceType;
import com.mydata.datasources.SyncMode;
import com.mydata.support.PostgresIntegrationTest;
import com.mydata.users.UserEntity;
import com.mydata.users.UserRepository;
import com.mydata.workspaces.WorkspaceEntity;
import com.mydata.workspaces.WorkspaceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class CorePersistenceTest extends PostgresIntegrationTest {
    @Autowired UserRepository users;
    @Autowired WorkspaceRepository workspaces;
    @Autowired DataSourceRepository dataSources;
    @Autowired ExternalDocumentRepository documents;
    @Autowired DocumentChunkRepository chunks;

    @Test
    void persistsDocumentWithAclAndChunks() {
        UserEntity user = users.save(UserEntity.create("owner@example.com", "Owner"));
        WorkspaceEntity workspace = workspaces.save(WorkspaceEntity.create(user.getId(), "Personal"));
        DataSourceEntity source = dataSources.save(DataSourceEntity.create(
            workspace.getId(),
            DataSourceType.LOCAL_TEXT,
            "Local notes",
            DataSourceStatus.ACTIVE,
            SyncMode.MANUAL
        ));

        ExternalDocumentEntity document = documents.save(ExternalDocumentEntity.create(
            workspace.getId(),
            source.getId(),
            "note-1",
            "LOCAL_TEXT",
            "First note",
            "hash-1"
        ));
        document.addAcl(DocumentAclEntryEntity.read(document, PrincipalKeys.user(user.getId()), "MANUAL", false));
        document.addChunk(DocumentChunkEntity.create(document, 0, "hello private note", 3));
        documents.saveAndFlush(document);

        ExternalDocumentEntity reloaded = documents.findById(document.getId()).orElseThrow();

        assertThat(reloaded.getAclEntries()).hasSize(1);
        assertThat(reloaded.getChunks()).hasSize(1);
        assertThat(chunks.findByDocumentIdOrderByChunkIndex(document.getId()))
            .extracting(DocumentChunkEntity::getContent)
            .containsExactly("hello private note");
    }
}
