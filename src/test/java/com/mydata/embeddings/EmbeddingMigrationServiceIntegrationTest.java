package com.mydata.embeddings;

import com.mydata.datasources.DataSourceEntity;
import com.mydata.datasources.DataSourceRepository;
import com.mydata.datasources.DataSourceStatus;
import com.mydata.datasources.DataSourceType;
import com.mydata.datasources.SyncMode;
import com.mydata.documents.DocumentChunkEntity;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class EmbeddingMigrationServiceIntegrationTest extends PostgresIntegrationTest {
    @Autowired UserRepository users;
    @Autowired WorkspaceRepository workspaces;
    @Autowired DataSourceRepository dataSources;
    @Autowired ExternalDocumentRepository documents;
    @Autowired DocumentChunkRepository chunks;
    @Autowired DocumentEmbeddingRepository embeddings;
    @Autowired EmbeddingMigrationService migration;
    @Autowired StubEmbeddingClient embeddingClient;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetEmbeddingClient() {
        embeddingClient.reset();
    }

    @Test
    void calculatesCoverageFromActiveDocumentsOnly() {
        Fixture fixture = fixture("coverage");
        List<DocumentChunkEntity> firstDocument = document(
            fixture,
            "active-1",
            List.of("첫 번째 활성 청크", "두 번째 활성 청크")
        );
        List<DocumentChunkEntity> secondDocument = document(
            fixture,
            "active-2",
            List.of("세 번째 활성 청크")
        );
        List<DocumentChunkEntity> deletedDocument = document(
            fixture,
            "deleted",
            List.of("삭제된 문서 청크")
        );
        jdbcTemplate.update(
            "UPDATE external_documents SET deleted_at = now() WHERE id = ?",
            deletedDocument.getFirst().getDocument().getId()
        );

        embeddings.upsert(firstDocument.getFirst().getId(), embeddingClient.model(), vector(0.1f));
        embeddings.upsert(secondDocument.getFirst().getId(), embeddingClient.model(), vector(0.2f));
        embeddings.upsert(deletedDocument.getFirst().getId(), embeddingClient.model(), vector(0.3f));

        EmbeddingCoverageProjection coverage = chunks.coverageByDataSource(
            fixture.dataSource().getId(),
            embeddingClient.model()
        );

        assertThat(coverage.getTotalChunks()).isEqualTo(3);
        assertThat(coverage.getCoveredChunks()).isEqualTo(2);
    }

    @Test
    void fillsOnlyMissingEmbeddingsAndRemainsIdempotent() {
        Fixture fixture = fixture("idempotent");
        List<DocumentChunkEntity> documentChunks = document(
            fixture,
            "active",
            List.of("이미 채워진 청크", "새 청크 하나", "새 청크 둘")
        );
        embeddings.upsert(documentChunks.getFirst().getId(), embeddingClient.model(), vector(0.4f));

        migration.reembedLoop(fixture.dataSource().getId());
        migration.reembedLoop(fixture.dataSource().getId());

        EmbeddingMigrationService.EmbeddingCoverage coverage = migration.coverage(fixture.dataSource().getId());
        assertThat(coverage.totalChunks()).isEqualTo(3);
        assertThat(coverage.coveredChunks()).isEqualTo(3);
        assertThat(embeddingClient.callsFor("이미 채워진 청크")).isZero();
        assertThat(embeddingClient.callsFor("새 청크 하나")).isEqualTo(1);
        assertThat(embeddingClient.callsFor("새 청크 둘")).isEqualTo(1);
    }

    @Test
    void continuesAfterChunkFailureAndFillsItOnRetry() {
        Fixture fixture = fixture("failure");
        document(
            fixture,
            "active",
            List.of("정상 청크 하나", "실패할 청크", "정상 청크 둘")
        );
        embeddingClient.failOn("실패할 청크");

        migration.reembedLoop(fixture.dataSource().getId());

        EmbeddingMigrationService.EmbeddingCoverage firstCoverage = migration.coverage(fixture.dataSource().getId());
        assertThat(firstCoverage.totalChunks()).isEqualTo(3);
        assertThat(firstCoverage.coveredChunks()).isEqualTo(2);

        embeddingClient.clearFailures();
        migration.reembedLoop(fixture.dataSource().getId());

        EmbeddingMigrationService.EmbeddingCoverage retriedCoverage = migration.coverage(fixture.dataSource().getId());
        assertThat(retriedCoverage.coveredChunks()).isEqualTo(3);
        assertThat(embeddingClient.callsFor("정상 청크 하나")).isEqualTo(1);
        assertThat(embeddingClient.callsFor("실패할 청크")).isEqualTo(2);
        assertThat(embeddingClient.callsFor("정상 청크 둘")).isEqualTo(1);
    }

    private Fixture fixture(String prefix) {
        String suffix = UUID.randomUUID().toString();
        UserEntity owner = users.save(UserEntity.create(
            prefix + "-owner-" + suffix + "@example.com",
            "소유자"
        ));
        WorkspaceEntity workspace = workspaces.save(WorkspaceEntity.create(owner.getId(), prefix + " 워크스페이스"));
        DataSourceEntity dataSource = dataSources.saveAndFlush(DataSourceEntity.create(
            workspace.getId(),
            DataSourceType.LOCAL_TEXT,
            prefix + " 데이터소스",
            DataSourceStatus.ACTIVE,
            SyncMode.MANUAL
        ));
        return new Fixture(workspace, dataSource);
    }

    private List<DocumentChunkEntity> document(Fixture fixture, String externalId, List<String> contents) {
        ExternalDocumentEntity document = ExternalDocumentEntity.create(
            fixture.workspace().getId(),
            fixture.dataSource().getId(),
            externalId,
            DataSourceType.LOCAL_TEXT.name(),
            externalId,
            "hash-" + externalId
        );
        for (int index = 0; index < contents.size(); index++) {
            document.addChunk(DocumentChunkEntity.create(document, index, contents.get(index), null));
        }
        document = documents.saveAndFlush(document);
        return chunks.findByDocumentIdOrderByChunkIndex(document.getId());
    }

    private float[] vector(float value) {
        float[] vector = new float[embeddingClient.dimensions()];
        Arrays.fill(vector, value);
        return vector;
    }

    private record Fixture(WorkspaceEntity workspace, DataSourceEntity dataSource) {
    }

    @TestConfiguration
    static class StubEmbeddingConfiguration {
        @Bean
        @Primary
        StubEmbeddingClient stubEmbeddingClient() {
            return new StubEmbeddingClient();
        }
    }

    static final class StubEmbeddingClient implements EmbeddingClient {
        private static final int DIMENSIONS = 1536;
        private final Set<String> failingContents = new HashSet<>();
        private final Map<String, Integer> calls = new HashMap<>();

        @Override
        public String model() {
            return "deterministic-1536";
        }

        @Override
        public int dimensions() {
            return DIMENSIONS;
        }

        @Override
        public float[] embed(String text) {
            calls.merge(text, 1, Integer::sum);
            if (failingContents.contains(text)) {
                throw new IllegalStateException("테스트 임베딩 생성에 실패했습니다");
            }
            float[] vector = new float[DIMENSIONS];
            Arrays.fill(vector, Math.floorMod(text.hashCode(), 1000) / 1000.0f);
            return vector;
        }

        void reset() {
            failingContents.clear();
            calls.clear();
        }

        void failOn(String content) {
            failingContents.add(content);
        }

        void clearFailures() {
            failingContents.clear();
        }

        int callsFor(String content) {
            return calls.getOrDefault(content, 0);
        }
    }
}
