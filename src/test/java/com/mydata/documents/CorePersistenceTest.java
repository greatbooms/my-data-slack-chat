package com.mydata.documents;

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
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class CorePersistenceTest extends PostgresIntegrationTest {
    @Autowired UserRepository users;
    @Autowired WorkspaceRepository workspaces;
    @Autowired DataSourceRepository dataSources;
    @Autowired ExternalDocumentRepository documents;
    @Autowired DocumentChunkRepository chunks;
    @Autowired EntityManager entityManager;

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
        source.putConfig("externalId", "note-1");
        source = dataSources.saveAndFlush(source);

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

        entityManager.clear();

        ExternalDocumentEntity reloaded = documents.findById(document.getId()).orElseThrow();
        DataSourceEntity reloadedSource = dataSources.findById(source.getId()).orElseThrow();
        ExternalDocumentEntity foundByExternalId = documents
            .findByDataSourceIdAndExternalId(source.getId(), "note-1")
            .orElseThrow();

        assertThat(reloadedSource.configValue("externalId")).isEqualTo("note-1");
        assertThat(foundByExternalId.getId()).isEqualTo(document.getId());
        assertThat(reloaded.getAclEntries()).hasSize(1);
        assertThat(reloaded.getChunks()).hasSize(1);
        assertThat(chunks.findByDocumentIdOrderByChunkIndex(document.getId()))
            .extracting(DocumentChunkEntity::getContent)
            .containsExactly("hello private note");

        reloaded.replaceChunks(List.of(DocumentChunkEntity.create(reloaded, 1, "replacement private note", 3)));
        documents.saveAndFlush(reloaded);
        entityManager.clear();

        assertThat(chunks.findByDocumentIdOrderByChunkIndex(document.getId()))
            .extracting(DocumentChunkEntity::getContent)
            .containsExactly("replacement private note");
    }
}
