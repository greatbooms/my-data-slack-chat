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
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceUnitUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class CorePersistenceTest extends PostgresIntegrationTest {
    @Autowired UserRepository users;
    @Autowired WorkspaceRepository workspaces;
    @Autowired DataSourceRepository dataSources;
    @Autowired ExternalDocumentRepository documents;
    @Autowired DocumentAclEntryRepository aclEntries;
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
        PersistenceUnitUtil persistenceUnitUtil = entityManager.getEntityManagerFactory().getPersistenceUnitUtil();

        assertThat(reloadedSource.configValue("externalId")).isEqualTo("note-1");
        assertThat(foundByExternalId.getId()).isEqualTo(document.getId());
        assertThat(persistenceUnitUtil.isLoaded(reloaded, "aclEntries")).isFalse();
        assertThat(persistenceUnitUtil.isLoaded(reloaded, "chunks")).isFalse();
        assertThat(reloaded.getAclEntries()).hasSize(1);
        assertThat(reloaded.getChunks()).hasSize(1);
        assertThat(chunks.findByDocumentIdOrderByChunkIndex(document.getId()))
            .extracting(DocumentChunkEntity::getContent)
            .containsExactly("hello private note");

        entityManager.clear();

        var existingChunks = chunks.findByDocumentIdOrderByChunkIndex(document.getId());
        assertThat(existingChunks).hasSize(1);
        chunks.deleteAll(existingChunks);
        chunks.flush();

        var existingAclEntries = aclEntries.findByDocumentId(document.getId());
        assertThat(existingAclEntries).hasSize(1);
        aclEntries.deleteAll(existingAclEntries);
        aclEntries.flush();
        entityManager.clear();

        ExternalDocumentEntity replacementTarget = documents.findById(document.getId()).orElseThrow();
        replacementTarget.addAcl(DocumentAclEntryEntity.read(
            replacementTarget,
            PrincipalKeys.user(user.getId()),
            "REFRESHED",
            false
        ));
        replacementTarget.addChunk(DocumentChunkEntity.create(replacementTarget, 0, "replacement private note", 3));
        documents.saveAndFlush(replacementTarget);
        entityManager.clear();

        assertThat(aclEntries.findByDocumentId(document.getId()))
            .hasSize(1)
            .first()
            .satisfies(acl -> {
                assertThat(acl.getPrincipalKey()).isEqualTo(PrincipalKeys.user(user.getId()));
                assertThat(acl.getPermission()).isEqualTo(Permission.READ);
                assertThat(acl.getSource()).isEqualTo("REFRESHED");
            });
        assertThat(chunks.findByDocumentIdOrderByChunkIndex(document.getId()))
            .extracting(DocumentChunkEntity::getContent)
            .containsExactly("replacement private note");
    }
}
