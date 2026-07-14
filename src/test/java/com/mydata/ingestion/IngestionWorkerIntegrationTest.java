package com.mydata.ingestion;

import com.mydata.connectors.core.ConnectorDocumentEvent;
import com.mydata.connectors.core.ConnectorEventSink;
import com.mydata.connectors.core.ConnectorFailureEvent;
import com.mydata.connectors.core.ConnectorFailureStage;
import com.mydata.connectors.core.ConnectorItemReference;
import com.mydata.connectors.core.ConnectorItemType;
import com.mydata.connectors.core.ConnectorReconciliationMode;
import com.mydata.connectors.core.DataSourceConnector;
import com.mydata.connectors.core.DataSourceSnapshot;
import com.mydata.connectors.core.RawAclEntry;
import com.mydata.connectors.core.RawContent;
import com.mydata.connectors.core.RawExternalDocument;
import com.mydata.connectors.core.SyncCursor;
import com.mydata.datasources.DataSourceEntity;
import com.mydata.datasources.DataSourceRepository;
import com.mydata.datasources.DataSourceStatus;
import com.mydata.datasources.DataSourceType;
import com.mydata.datasources.SyncMode;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class IngestionWorkerIntegrationTest extends PostgresIntegrationTest {
    @Autowired UserRepository users;
    @Autowired WorkspaceRepository workspaces;
    @Autowired DataSourceRepository dataSources;
    @Autowired IngestionJobRepository ingestionJobs;
    @Autowired IngestionJobItemRepository jobItems;
    @Autowired ExternalDocumentRepository documents;
    @Autowired IngestionWorker worker;
    @Autowired TestConnector connector;
    @Autowired JobItemSaveFault jobItemSaveFault;
    @Autowired TransactionTemplate callerTransactions;

    @BeforeEach
    void resetConnector() {
        connector.reset();
        jobItemSaveFault.reset();
    }

    @Test
    void connectorRunsOutsideDatabaseTransaction() {
        Fixture fixture = fixture("outside-tx", Map.of("cursor", "before"));
        connector.events(documentEvent("first", readableDocument("first")));
        AtomicBoolean callerTransactionActive = new AtomicBoolean();

        callerTransactions.executeWithoutResult(status -> {
            callerTransactionActive.set(TransactionSynchronizationManager.isActualTransactionActive());
            worker.run(fixture.job().getId());
        });

        assertThat(callerTransactionActive).isTrue();
        assertThat(connector.transactionActiveDuringFetch()).isFalse();
        assertThat(ingestionJobs.findById(fixture.job().getId()).orElseThrow().getStatus())
            .isEqualTo(IngestionJobStatus.SUCCEEDED);
    }

    @Test
    void secondDocumentPersistenceFailureRollsBackOnlyThatDocumentAndContinuesWithThird() {
        Fixture fixture = fixture("partial", Map.of("cursor", "before"));
        connector.events(
            documentEvent("first", readableDocument("first")),
            documentEvent("second", documentWithUnsupportedAcl("second")),
            documentEvent("third", readableDocument("third"))
        );

        worker.run(fixture.job().getId());

        assertThat(documents.findByDataSourceIdAndExternalId(fixture.dataSource().getId(), "first")).isPresent();
        assertThat(documents.findByDataSourceIdAndExternalId(fixture.dataSource().getId(), "second")).isEmpty();
        assertThat(documents.findByDataSourceIdAndExternalId(fixture.dataSource().getId(), "third")).isPresent();
        assertThat(jobItems.countByJobIdAndStatus(fixture.job().getId(), IngestionJobItemStatus.SUCCEEDED))
            .isEqualTo(2);
        assertThat(jobItems.countByJobIdAndStatus(fixture.job().getId(), IngestionJobItemStatus.FAILED))
            .isEqualTo(1);
        assertThat(jobItems.findByJobIdOrderByProcessedAtAscIdAsc(fixture.job().getId()))
            .filteredOn(item -> item.getStatus() == IngestionJobItemStatus.SUCCEEDED)
            .allSatisfy(item -> {
                assertThat(item.getDocumentId()).isNotNull();
                assertThat(item.getReason()).isNull();
            });
        assertThat(jobItems.findByJobIdOrderByProcessedAtAscIdAsc(fixture.job().getId()))
            .filteredOn(item -> item.getStatus() == IngestionJobItemStatus.FAILED)
            .singleElement()
            .satisfies(item -> {
                assertThat(item.getExternalId()).isEqualTo("data-source:second");
                assertThat(item.getDocumentId()).isNull();
                assertThat(item.getReason())
                    .isEqualTo("[DATA_SOURCE] second (PERSIST): 문서 저장에 실패했습니다");
            });
        assertThat(ingestionJobs.findById(fixture.job().getId()).orElseThrow())
            .satisfies(job -> {
                assertThat(job.getStatus()).isEqualTo(IngestionJobStatus.PARTIAL_FAILED);
                assertThat(job.getErrorMessage()).isEqualTo("전체 3개 중 1개 실패");
                assertThat(job.getFinishedAt()).isNotNull();
            });
        assertThat(dataSources.findById(fixture.dataSource().getId()).orElseThrow().syncCursorValue())
            .containsEntry("cursor", "before");
        assertThat(dataSources.findById(fixture.dataSource().getId()).orElseThrow().getLastSyncedAt())
            .isNull();
    }

    @Test
    void succeededItemPersistenceFailureRollsBackDocumentAbortsConnectorAndMarksJobFailed() {
        Fixture fixture = fixture("item-save-failure", Map.of("cursor", "before"));
        connector.events(
            documentEvent("first", readableDocument("first")),
            documentEvent("second", readableDocument("second")),
            documentEvent("third", readableDocument("third"))
        );
        jobItemSaveFault.failOn("data-source:second", IngestionJobItemStatus.SUCCEEDED);

        worker.run(fixture.job().getId());

        assertThat(documents.findByDataSourceIdAndExternalId(fixture.dataSource().getId(), "first")).isPresent();
        assertThat(documents.findByDataSourceIdAndExternalId(fixture.dataSource().getId(), "second")).isEmpty();
        assertThat(documents.findByDataSourceIdAndExternalId(fixture.dataSource().getId(), "third")).isEmpty();
        assertThat(jobItems.findByJobIdOrderByProcessedAtAscIdAsc(fixture.job().getId()))
            .hasSize(1)
            .singleElement()
            .satisfies(item -> {
                assertThat(item.getExternalId()).isEqualTo("data-source:first");
                assertThat(item.getStatus()).isEqualTo(IngestionJobItemStatus.SUCCEEDED);
                assertThat(item.getDocumentId()).isNotNull();
                assertThat(item.getReason()).isNull();
            });
        assertThat(ingestionJobs.findById(fixture.job().getId()).orElseThrow())
            .satisfies(job -> {
                assertThat(job.getStatus()).isEqualTo(IngestionJobStatus.FAILED);
                assertThat(job.getErrorMessage()).isEqualTo("job item infrastructure failure");
            });
        assertThat(dataSources.findById(fixture.dataSource().getId()).orElseThrow())
            .satisfies(source -> {
                assertThat(source.syncCursorValue()).containsEntry("cursor", "before");
                assertThat(source.getLastSyncedAt()).isNull();
            });
    }

    @Test
    void failedItemPersistenceFailureAbortsConnectorAndMarksJobFailed() {
        Fixture fixture = fixture("failure-item-save", Map.of("cursor", "before"));
        connector.events(
            documentEvent("second", documentWithUnsupportedAcl("second")),
            documentEvent("third", readableDocument("third"))
        );
        jobItemSaveFault.failOn("data-source:second", IngestionJobItemStatus.FAILED);

        worker.run(fixture.job().getId());

        assertThat(jobItems.findByJobIdOrderByProcessedAtAscIdAsc(fixture.job().getId())).isEmpty();
        assertThat(documents.findByDataSourceIdAndExternalId(fixture.dataSource().getId(), "second")).isEmpty();
        assertThat(documents.findByDataSourceIdAndExternalId(fixture.dataSource().getId(), "third")).isEmpty();
        assertThat(ingestionJobs.findById(fixture.job().getId()).orElseThrow())
            .satisfies(job -> {
                assertThat(job.getStatus()).isEqualTo(IngestionJobStatus.FAILED);
                assertThat(job.getErrorMessage()).isEqualTo("job item infrastructure failure");
            });
        assertThat(dataSources.findById(fixture.dataSource().getId()).orElseThrow())
            .satisfies(source -> {
                assertThat(source.syncCursorValue()).containsEntry("cursor", "before");
                assertThat(source.getLastSyncedAt()).isNull();
            });
    }

    @Test
    void allSuccessUpdatesCursorAndLastSyncedAt() {
        Fixture fixture = fixture("success", Map.of("cursor", "before"));
        connector.events(
            documentEvent("first", readableDocument("first")),
            documentEvent("second", readableDocument("second"))
        );

        worker.run(fixture.job().getId());

        assertThat(ingestionJobs.findById(fixture.job().getId()).orElseThrow().getStatus())
            .isEqualTo(IngestionJobStatus.SUCCEEDED);
        assertThat(dataSources.findById(fixture.dataSource().getId()).orElseThrow())
            .satisfies(source -> {
                assertThat(source.syncCursorValue()).containsEntry("cursor", "after");
                assertThat(source.getLastSyncedAt()).isNotNull();
            });
        assertThat(jobItems.findByJobIdOrderByProcessedAtAscIdAsc(fixture.job().getId()))
            .hasSize(2)
            .extracting(IngestionJobItemEntity::getExternalId)
            .containsExactlyInAnyOrder("data-source:first", "data-source:second");
        assertThat(jobItems.findByJobIdOrderByProcessedAtAscIdAsc(fixture.job().getId()))
            .allSatisfy(item -> {
                assertThat(item.getStatus()).isEqualTo(IngestionJobItemStatus.SUCCEEDED);
                assertThat(item.getDocumentId()).isNotNull();
                assertThat(item.getReason()).isNull();
            });
    }

    @Test
    void successfulFullSnapshotSoftDeletesOnlyUnseenDocuments() {
        Fixture fixture = fixture("full-snapshot", Map.of("cursor", "before"));
        connector.fullSnapshot();
        connector.events(
            documentEvent("seen", readableDocument("seen")),
            documentEvent("missing", readableDocument("missing"))
        );
        worker.run(fixture.job().getId());
        UUID seenId = documents.findByDataSourceIdAndExternalId(
            fixture.dataSource().getId(), "seen"
        ).orElseThrow().getId();
        UUID missingId = documents.findByDataSourceIdAndExternalId(
            fixture.dataSource().getId(), "missing"
        ).orElseThrow().getId();

        Fixture otherFixture = fixture("other-source", Map.of("cursor", "before"));
        connector.events(documentEvent("other", readableDocument("other")));
        worker.run(otherFixture.job().getId());
        UUID otherId = documents.findByDataSourceIdAndExternalId(
            otherFixture.dataSource().getId(), "other"
        ).orElseThrow().getId();

        connector.events(documentEvent("seen", readableDocument("seen")));
        IngestionJobEntity nextJob = pendingJob(fixture);
        worker.run(nextJob.getId());

        assertThat(documents.findById(seenId).orElseThrow().getDeletedAt()).isNull();
        assertThat(documents.findById(missingId).orElseThrow().getDeletedAt()).isNotNull();
        assertThat(documents.findById(otherId).orElseThrow().getDeletedAt()).isNull();
        assertThat(ingestionJobs.findById(nextJob.getId()).orElseThrow().getStatus())
            .isEqualTo(IngestionJobStatus.SUCCEEDED);
    }

    @Test
    void emptySuccessfulFullSnapshotSoftDeletesAllSourceDocuments() {
        Fixture fixture = fixture("empty-full-snapshot", Map.of("cursor", "before"));
        connector.fullSnapshot();
        connector.events(documentEvent("missing", readableDocument("missing")));
        worker.run(fixture.job().getId());
        UUID missingId = documents.findByDataSourceIdAndExternalId(
            fixture.dataSource().getId(), "missing"
        ).orElseThrow().getId();

        connector.events();
        IngestionJobEntity emptyJob = pendingJob(fixture);
        worker.run(emptyJob.getId());

        assertThat(documents.findById(missingId).orElseThrow().getDeletedAt()).isNotNull();
        assertThat(ingestionJobs.findById(emptyJob.getId()).orElseThrow().getStatus())
            .isEqualTo(IngestionJobStatus.SUCCEEDED);
    }

    @Test
    void partialFailedFullSnapshotDoesNotSoftDeleteUnseenDocuments() {
        Fixture fixture = fixture("partial-full-snapshot", Map.of("cursor", "before"));
        connector.fullSnapshot();
        connector.events(
            documentEvent("seen", readableDocument("seen")),
            documentEvent("missing", readableDocument("missing"))
        );
        worker.run(fixture.job().getId());
        UUID missingId = documents.findByDataSourceIdAndExternalId(
            fixture.dataSource().getId(), "missing"
        ).orElseThrow().getId();

        connector.events(documentEvent("seen", readableDocument("seen")));
        connector.failures(failureEvent("missing", "일시적으로 접근할 수 없습니다"));
        IngestionJobEntity partialJob = pendingJob(fixture);
        worker.run(partialJob.getId());

        assertThat(ingestionJobs.findById(partialJob.getId()).orElseThrow().getStatus())
            .isEqualTo(IngestionJobStatus.PARTIAL_FAILED);
        assertThat(documents.findById(missingId).orElseThrow().getDeletedAt()).isNull();
    }

    @Test
    void allFailedFullSnapshotDoesNotSoftDeleteUnseenDocuments() {
        Fixture fixture = fixture("failed-full-snapshot", Map.of("cursor", "before"));
        connector.fullSnapshot();
        connector.events(documentEvent("missing", readableDocument("missing")));
        worker.run(fixture.job().getId());
        UUID missingId = documents.findByDataSourceIdAndExternalId(
            fixture.dataSource().getId(), "missing"
        ).orElseThrow().getId();

        connector.events();
        connector.failures(failureEvent("root", "루트를 읽지 못했습니다"));
        IngestionJobEntity failedJob = pendingJob(fixture);
        worker.run(failedJob.getId());

        assertThat(ingestionJobs.findById(failedJob.getId()).orElseThrow().getStatus())
            .isEqualTo(IngestionJobStatus.FAILED);
        assertThat(documents.findById(missingId).orElseThrow().getDeletedAt()).isNull();
    }

    @Test
    void connectorFailureAfterDocumentDoesNotSoftDeleteUnseenDocuments() {
        Fixture fixture = fixture("interrupted-full-snapshot", Map.of("cursor", "before"));
        connector.fullSnapshot();
        connector.events(
            documentEvent("seen", readableDocument("seen")),
            documentEvent("missing", readableDocument("missing"))
        );
        worker.run(fixture.job().getId());
        UUID missingId = documents.findByDataSourceIdAndExternalId(
            fixture.dataSource().getId(), "missing"
        ).orElseThrow().getId();

        connector.events(documentEvent("seen", readableDocument("seen")));
        connector.throwAfterDocuments(new IllegalStateException("source interrupted"));
        IngestionJobEntity interruptedJob = pendingJob(fixture);
        worker.run(interruptedJob.getId());

        assertThat(ingestionJobs.findById(interruptedJob.getId()).orElseThrow().getStatus())
            .isEqualTo(IngestionJobStatus.FAILED);
        assertThat(documents.findById(missingId).orElseThrow().getDeletedAt()).isNull();
    }

    @Test
    void allFailuresMarkFailedAndKeepCursorAndLastSyncedAt() {
        Fixture fixture = fixture("failed", Map.of("cursor", "before"));
        connector.failures(
            failureEvent("first", "첫 번째 문서를 가져오지 못했습니다"),
            failureEvent("second", "두 번째 문서를 가져오지 못했습니다")
        );

        worker.run(fixture.job().getId());

        assertThat(ingestionJobs.findById(fixture.job().getId()).orElseThrow())
            .satisfies(job -> {
                assertThat(job.getStatus()).isEqualTo(IngestionJobStatus.FAILED);
                assertThat(job.getErrorMessage()).isEqualTo("전체 2개 항목 수집 실패");
                assertThat(job.getFinishedAt()).isNotNull();
            });
        assertThat(jobItems.countByJobIdAndStatus(fixture.job().getId(), IngestionJobItemStatus.SUCCEEDED))
            .isZero();
        assertThat(jobItems.countByJobIdAndStatus(fixture.job().getId(), IngestionJobItemStatus.FAILED))
            .isEqualTo(2);
        assertThat(dataSources.findById(fixture.dataSource().getId()).orElseThrow())
            .satisfies(source -> {
                assertThat(source.syncCursorValue()).containsEntry("cursor", "before");
                assertThat(source.getLastSyncedAt()).isNull();
            });
    }

    @Test
    void emptyRunSucceeds() {
        Fixture fixture = fixture("empty", Map.of("cursor", "before"));

        worker.run(fixture.job().getId());

        assertThat(ingestionJobs.findById(fixture.job().getId()).orElseThrow().getStatus())
            .isEqualTo(IngestionJobStatus.SUCCEEDED);
        assertThat(jobItems.findByJobIdOrderByProcessedAtAscIdAsc(fixture.job().getId())).isEmpty();
        assertThat(dataSources.findById(fixture.dataSource().getId()).orElseThrow())
            .satisfies(source -> {
                assertThat(source.syncCursorValue()).containsEntry("cursor", "after");
                assertThat(source.getLastSyncedAt()).isNotNull();
            });
    }

    @Test
    void alreadyClaimedJobIsNotRunAgain() {
        Fixture fixture = fixture("claimed", Map.of("cursor", "before"));
        connector.events(documentEvent("first", readableDocument("first")));

        worker.run(fixture.job().getId());
        worker.run(fixture.job().getId());

        assertThat(connector.fetchCount()).isEqualTo(1);
        assertThat(jobItems.findByJobIdOrderByProcessedAtAscIdAsc(fixture.job().getId())).hasSize(1);
    }

    @Test
    void topLevelConnectorFailureMarksJobFailed() {
        Fixture fixture = fixture("connector-failure", Map.of("cursor", "before"));
        connector.throwOnFetch(new IllegalStateException("커넥터 호출 실패"));

        worker.run(fixture.job().getId());

        assertThat(ingestionJobs.findById(fixture.job().getId()).orElseThrow())
            .satisfies(job -> {
                assertThat(job.getStatus()).isEqualTo(IngestionJobStatus.FAILED);
                assertThat(job.getErrorMessage()).isEqualTo("커넥터 호출 실패");
            });
        assertThat(jobItems.findByJobIdOrderByProcessedAtAscIdAsc(fixture.job().getId())).isEmpty();
        assertThat(dataSources.findById(fixture.dataSource().getId()).orElseThrow())
            .satisfies(source -> {
                assertThat(source.syncCursorValue()).containsEntry("cursor", "before");
                assertThat(source.getLastSyncedAt()).isNull();
            });
    }

    private record Fixture(DataSourceEntity dataSource, IngestionJobEntity job) {
    }

    private Fixture fixture(String name, Map<String, Object> cursor) {
        String suffix = UUID.randomUUID().toString();
        UserEntity owner = users.save(UserEntity.create(
            "worker-" + suffix + "@example.com", "Worker Owner"
        ));
        WorkspaceEntity workspace = workspaces.save(WorkspaceEntity.create(owner.getId(), name));
        DataSourceEntity source = DataSourceEntity.create(
            workspace.getId(), DataSourceType.GOOGLE_DRIVE, name, DataSourceStatus.ACTIVE, SyncMode.MANUAL
        );
        source.assignOwner(owner.getId());
        source.replaceSyncCursor(cursor);
        source = dataSources.saveAndFlush(source);
        IngestionJobEntity job = ingestionJobs.saveAndFlush(IngestionJobEntity.pending(
            workspace.getId(), source.getId(), IngestionTriggerType.MANUAL, owner.getId()
        ));
        return new Fixture(source, job);
    }

    private IngestionJobEntity pendingJob(Fixture fixture) {
        return ingestionJobs.saveAndFlush(IngestionJobEntity.pending(
            fixture.dataSource().getWorkspaceId(),
            fixture.dataSource().getId(),
            IngestionTriggerType.MANUAL,
            fixture.job().getRequestedByUserId()
        ));
    }

    private ConnectorDocumentEvent documentEvent(String id, RawExternalDocument document) {
        return new ConnectorDocumentEvent(
            document,
            reference(id)
        );
    }

    private ConnectorFailureEvent failureEvent(String id, String reason) {
        return new ConnectorFailureEvent(reference(id), ConnectorFailureStage.RETRIEVE, reason);
    }

    private ConnectorItemReference reference(String id) {
        return new ConnectorItemReference(ConnectorItemType.DATA_SOURCE, id, id, List.of(id));
    }

    private RawExternalDocument readableDocument(String id) {
        return rawDocument(id, "READ");
    }

    private RawExternalDocument documentWithUnsupportedAcl(String id) {
        return rawDocument(id, "WRITE");
    }

    private RawExternalDocument rawDocument(String id, String permission) {
        return new RawExternalDocument(
            id, DataSourceType.GOOGLE_DRIVE, id, null, "text/plain", null, null,
            id + "-hash", Map.of(), new RawContent(id + " content", "text/plain"),
            List.of(new RawAclEntry("WORKSPACE:qa", permission, false, "TEST"))
        );
    }

    @TestConfiguration
    static class TestConnectorConfiguration {
        @Bean
        TestConnector testConnector() {
            return new TestConnector();
        }

        @Bean
        JobItemSaveFault jobItemSaveFault() {
            return new JobItemSaveFault();
        }

        @Bean
        @Primary
        IngestionJobItemRepository faultInjectingJobItems(
            @Qualifier("ingestionJobItemRepository") IngestionJobItemRepository delegate,
            JobItemSaveFault fault
        ) {
            return (IngestionJobItemRepository) Proxy.newProxyInstance(
                IngestionJobItemRepository.class.getClassLoader(),
                new Class<?>[] {IngestionJobItemRepository.class},
                (proxy, method, arguments) -> {
                    if (
                        ("save".equals(method.getName()) || "saveAndFlush".equals(method.getName()))
                            && arguments != null
                            && arguments.length == 1
                            && arguments[0] instanceof IngestionJobItemEntity item
                            && fault.shouldFail(item)
                    ) {
                        throw new IllegalStateException("job item infrastructure failure");
                    }
                    try {
                        return method.invoke(delegate, arguments);
                    } catch (InvocationTargetException exception) {
                        throw exception.getCause();
                    }
                }
            );
        }
    }

    static final class JobItemSaveFault {
        private String externalId;
        private IngestionJobItemStatus status;

        void reset() {
            externalId = null;
            status = null;
        }

        void failOn(String externalId, IngestionJobItemStatus status) {
            this.externalId = externalId;
            this.status = status;
        }

        boolean shouldFail(IngestionJobItemEntity item) {
            return externalId != null
                && externalId.equals(item.getExternalId())
                && status == item.getStatus();
        }
    }

    static final class TestConnector implements DataSourceConnector {
        private List<ConnectorDocumentEvent> documentEvents = List.of();
        private List<ConnectorFailureEvent> failureEvents = List.of();
        private boolean transactionActiveDuringFetch;
        private int fetchCount;
        private RuntimeException fetchFailure;
        private RuntimeException failureAfterDocuments;
        private ConnectorReconciliationMode reconciliationMode = ConnectorReconciliationMode.NONE;

        void reset() {
            documentEvents = List.of();
            failureEvents = List.of();
            transactionActiveDuringFetch = false;
            fetchCount = 0;
            fetchFailure = null;
            failureAfterDocuments = null;
            reconciliationMode = ConnectorReconciliationMode.NONE;
        }

        void events(ConnectorDocumentEvent... values) {
            documentEvents = List.of(values);
        }

        void failures(ConnectorFailureEvent... values) {
            failureEvents = List.of(values);
        }

        void throwOnFetch(RuntimeException failure) {
            fetchFailure = failure;
        }

        void throwAfterDocuments(RuntimeException failure) {
            failureAfterDocuments = failure;
        }

        void fullSnapshot() {
            reconciliationMode = ConnectorReconciliationMode.FULL_SNAPSHOT;
        }

        boolean transactionActiveDuringFetch() {
            return transactionActiveDuringFetch;
        }

        int fetchCount() {
            return fetchCount;
        }

        @Override
        public DataSourceType supports() {
            return DataSourceType.GOOGLE_DRIVE;
        }

        @Override
        public ConnectorReconciliationMode reconciliationMode() {
            return reconciliationMode;
        }

        @Override
        public SyncCursor fetchChanges(DataSourceSnapshot source, ConnectorEventSink sink) {
            fetchCount++;
            transactionActiveDuringFetch = TransactionSynchronizationManager.isActualTransactionActive();
            if (fetchFailure != null) {
                throw fetchFailure;
            }
            documentEvents.forEach(sink::onDocument);
            if (failureAfterDocuments != null) {
                throw failureAfterDocuments;
            }
            failureEvents.forEach(sink::onFailure);
            return new SyncCursor(Map.of("cursor", "after"));
        }
    }
}
