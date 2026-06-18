package com.mydata.retrieval;

import com.mydata.auth.PrincipalKeys;
import com.mydata.datasources.DataSourceEntity;
import com.mydata.datasources.DataSourceRepository;
import com.mydata.datasources.DataSourceStatus;
import com.mydata.datasources.DataSourceType;
import com.mydata.datasources.SyncMode;
import com.mydata.ingestion.IngestionJobEntity;
import com.mydata.ingestion.IngestionJobRepository;
import com.mydata.ingestion.IngestionTriggerType;
import com.mydata.ingestion.IngestionWorker;
import com.mydata.support.PostgresIntegrationTest;
import com.mydata.users.UserEntity;
import com.mydata.users.UserRepository;
import com.mydata.workspaces.WorkspaceEntity;
import com.mydata.workspaces.WorkspaceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PgVectorSearchRepositoryTest extends PostgresIntegrationTest {
    @Autowired UserRepository users;
    @Autowired WorkspaceRepository workspaces;
    @Autowired DataSourceRepository dataSources;
    @Autowired IngestionJobRepository jobs;
    @Autowired IngestionWorker worker;
    @Autowired RetrievalService retrievalService;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void returnsOnlyChunksVisibleToRequesterPrincipal() {
        UserEntity owner = users.save(UserEntity.create("retrieval-owner@example.com", "Retrieval Owner"));
        UserEntity stranger = users.save(UserEntity.create("retrieval-stranger@example.com", "Retrieval Stranger"));
        WorkspaceEntity workspace = workspaces.save(WorkspaceEntity.create(owner.getId(), "Retrieval workspace"));
        DataSourceEntity source = dataSources.save(DataSourceEntity.create(
            workspace.getId(),
            DataSourceType.LOCAL_TEXT,
            "Local notes",
            DataSourceStatus.ACTIVE,
            SyncMode.MANUAL
        ));
        source.putConfig("externalId", "note-1");
        source.putConfig("title", "Searchable note");
        source.putConfig("content", "project alpha budget planning");
        source.putConfig("uri", "local://note-1");
        source.putConfig("principalKey", PrincipalKeys.user(owner.getId()));
        source = dataSources.saveAndFlush(source);

        IngestionJobEntity job = jobs.saveAndFlush(IngestionJobEntity.pending(
            workspace.getId(),
            source.getId(),
            IngestionTriggerType.MANUAL,
            owner.getId()
        ));

        worker.run(job.getId());

        Integer embeddingCount = jdbcTemplate.queryForObject(
            """
            SELECT count(*)
            FROM document_embeddings e
            JOIN document_chunks c ON c.id = e.chunk_id
            JOIN external_documents d ON d.id = c.document_id
            WHERE d.data_source_id = ?
              AND d.external_id = ?
            """,
            Integer.class,
            source.getId(),
            "note-1"
        );
        List<RetrievedChunk> visible = retrievalService.retrieve(
            workspace.getId(),
            List.of(PrincipalKeys.user(owner.getId())),
            "alpha budget",
            5
        );
        List<RetrievedChunk> hidden = retrievalService.retrieve(
            workspace.getId(),
            List.of(PrincipalKeys.user(stranger.getId())),
            "alpha budget",
            5
        );

        assertThat(embeddingCount).isEqualTo(1);
        assertThat(visible).hasSize(1);
        assertThat(visible.getFirst().title()).isEqualTo("Searchable note");
        assertThat(visible.getFirst().uri()).isEqualTo("local://note-1");
        assertThat(hidden).isEmpty();
    }

    @Test
    void ignoresBlankRequesterPrincipalsEvenIfLegacyBlankAclRowsExist() {
        UserEntity owner = users.save(UserEntity.create("blank-retrieval-owner@example.com", "Blank Retrieval Owner"));
        WorkspaceEntity workspace = workspaces.save(WorkspaceEntity.create(owner.getId(), "Blank retrieval workspace"));
        DataSourceEntity source = dataSources.save(DataSourceEntity.create(
            workspace.getId(),
            DataSourceType.LOCAL_TEXT,
            "Local notes",
            DataSourceStatus.ACTIVE,
            SyncMode.MANUAL
        ));
        source.putConfig("externalId", "blank-acl-note");
        source.putConfig("title", "Blank ACL note");
        source.putConfig("content", "legacy blank acl content");
        source.putConfig("principalKey", PrincipalKeys.user(owner.getId()));
        source = dataSources.saveAndFlush(source);
        IngestionJobEntity job = jobs.saveAndFlush(IngestionJobEntity.pending(
            workspace.getId(),
            source.getId(),
            IngestionTriggerType.MANUAL,
            owner.getId()
        ));
        worker.run(job.getId());
        UUID documentId = jdbcTemplate.queryForObject(
            """
            SELECT id
            FROM external_documents
            WHERE data_source_id = ?
              AND external_id = ?
            """,
            UUID.class,
            source.getId(),
            "blank-acl-note"
        );
        jdbcTemplate.update(
            """
            INSERT INTO document_acl_entries (document_id, principal_key, permission, source)
            VALUES (?, '', 'READ', 'LEGACY')
            """,
            documentId
        );

        List<RetrievedChunk> hidden = retrievalService.retrieve(
            workspace.getId(),
            List.of("", "   "),
            "legacy blank",
            5
        );

        assertThat(hidden).isEmpty();
    }
}
