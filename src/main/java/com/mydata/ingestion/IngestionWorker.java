package com.mydata.ingestion;

import com.mydata.connectors.core.ConnectorDocumentEvent;
import com.mydata.connectors.core.ConnectorEventSink;
import com.mydata.connectors.core.ConnectorFailureEvent;
import com.mydata.connectors.core.ConnectorFailureStage;
import com.mydata.connectors.core.ConnectorReconciliationMode;
import com.mydata.connectors.core.DataSourceConnector;
import com.mydata.connectors.core.DataSourceSnapshot;
import com.mydata.connectors.core.SyncCursor;
import com.mydata.datasources.DataSourceEntity;
import com.mydata.datasources.DataSourceRepository;
import com.mydata.datasources.DataSourceType;
import com.mydata.documents.ExternalDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class IngestionWorker {
    private static final Logger log = LoggerFactory.getLogger(IngestionWorker.class);

    private final IngestionJobRepository ingestionJobs;
    private final IngestionJobItemRepository jobItems;
    private final DataSourceRepository dataSources;
    private final ExternalDocumentRepository documents;
    private final IngestionPipelineService pipeline;
    private final Map<DataSourceType, DataSourceConnector> connectors;
    private final TransactionTemplate transactions;

    public IngestionWorker(
        IngestionJobRepository ingestionJobs,
        IngestionJobItemRepository jobItems,
        DataSourceRepository dataSources,
        ExternalDocumentRepository documents,
        IngestionPipelineService pipeline,
        List<DataSourceConnector> connectors,
        TransactionTemplate transactions
    ) {
        this.ingestionJobs = ingestionJobs;
        this.jobItems = jobItems;
        this.dataSources = dataSources;
        this.documents = documents;
        this.pipeline = pipeline;
        this.transactions = transactions;
        this.connectors = new EnumMap<>(DataSourceType.class);
        for (DataSourceConnector connector : connectors) {
            this.connectors.put(connector.supports(), connector);
        }
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void run(UUID jobId) {
        log.info("수집 job 실행 시작: {}", jobId);
        if (!claimPendingJob(jobId)) {
            log.info("수집 job 실행 건너뜀: {} already claimed or completed", jobId);
            return;
        }
        try {
            ingestAndFinalize(jobId);
            log.info("수집 job 실행 완료: {}", jobId);
        } catch (RuntimeException exception) {
            log.warn("수집 job 실패: {}", jobId, exception);
            markFailed(jobId, exception.getMessage());
        }
    }

    private boolean claimPendingJob(UUID jobId) {
        return Boolean.TRUE.equals(transactions.execute(status -> {
            IngestionJobEntity job = loadJob(jobId);
            if (job.getStatus() != IngestionJobStatus.PENDING) {
                return false;
            }
            dataSources.findByIdForUpdate(job.getDataSourceId())
                .orElseThrow(() -> new IllegalStateException(
                    "데이터소스를 찾을 수 없습니다: " + job.getDataSourceId()
                ));
            return ingestionJobs.markPendingJobRunningIfDataSourceIdle(jobId) == 1;
        }));
    }

    private void ingestAndFinalize(UUID jobId) {
        SourceRunSnapshot sourceRun = transactions.execute(status -> loadSnapshot(jobId));
        DataSourceSnapshot source = Objects.requireNonNull(sourceRun).source();
        DataSourceConnector connector = requireConnector(source.type());
        SyncCursor nextCursor = connector.fetchChanges(source, new ConnectorEventSink() {
            @Override
            public void onDocument(ConnectorDocumentEvent event) {
                try {
                    transactions.executeWithoutResult(status -> {
                        UUID documentId = persistDocument(source, event);
                        jobItems.saveAndFlush(IngestionJobItemEntity.succeeded(
                            jobId, event.reference().qualifiedExternalId(), documentId
                        ));
                    });
                } catch (DocumentPersistenceException persistenceFailure) {
                    log.warn(
                        "수집 문서 저장 실패: job={}, externalId={}",
                        jobId,
                        event.reference().qualifiedExternalId(),
                        persistenceFailure.getCause()
                    );
                    persistFailure(jobId, new ConnectorFailureEvent(
                        event.reference(),
                        ConnectorFailureStage.PERSIST,
                        "문서 저장에 실패했습니다"
                    ));
                }
            }

            @Override
            public void onFailure(ConnectorFailureEvent event) {
                persistFailure(jobId, event);
            }
        });
        finalizeJob(jobId, sourceRun, nextCursor, connector.reconciliationMode());
    }

    private UUID persistDocument(DataSourceSnapshot source, ConnectorDocumentEvent event) {
        try {
            return pipeline.ingest(source.workspaceId(), source.id(), event.document());
        } catch (RuntimeException persistenceFailure) {
            throw new DocumentPersistenceException(persistenceFailure);
        }
    }

    private SourceRunSnapshot loadSnapshot(UUID jobId) {
        IngestionJobEntity job = loadJob(jobId);
        DataSourceEntity dataSource = dataSources.findActiveById(job.getDataSourceId())
            .orElseThrow(() -> new IllegalStateException("데이터소스를 찾을 수 없습니다: " + job.getDataSourceId()));
        return new SourceRunSnapshot(DataSourceSnapshot.from(dataSource), dataSource.getUpdatedAt());
    }

    private DataSourceConnector requireConnector(DataSourceType dataSourceType) {
        DataSourceConnector connector = connectors.get(dataSourceType);
        if (connector == null) {
            throw new IllegalStateException("등록된 커넥터가 없습니다: " + dataSourceType);
        }
        return connector;
    }

    private void persistFailure(UUID jobId, ConnectorFailureEvent event) {
        transactions.executeWithoutResult(status -> jobItems.save(IngestionJobItemEntity.failed(
            jobId,
            event.reference().qualifiedExternalId(),
            event.formattedReason()
        )));
    }

    private void finalizeJob(
        UUID jobId,
        SourceRunSnapshot sourceRun,
        SyncCursor nextCursor,
        ConnectorReconciliationMode reconciliationMode
    ) {
        Integer softDeletedDocumentCount = transactions.execute(status -> {
            long succeeded = jobItems.countByJobIdAndStatus(jobId, IngestionJobItemStatus.SUCCEEDED);
            long failed = jobItems.countByJobIdAndStatus(jobId, IngestionJobItemStatus.FAILED);
            IngestionJobEntity job = loadJob(jobId);
            if (failed == 0) {
                DataSourceEntity dataSource = dataSources.findActiveByIdForUpdate(sourceRun.source().id())
                    .orElseThrow(() -> new IllegalStateException(
                        "데이터소스를 찾을 수 없습니다: " + sourceRun.source().id()
                    ));
                if (!Objects.equals(dataSource.getUpdatedAt(), sourceRun.revision())) {
                    throw new IllegalStateException("수집 중 데이터소스 설정이 변경되었습니다");
                }
                job.markSucceeded();
                ingestionJobs.flush();
                int deletedCount = 0;
                if (reconciliationMode == ConnectorReconciliationMode.FULL_SNAPSHOT) {
                    deletedCount = documents
                        .softDeleteUnseenForSucceededFullSnapshot(jobId);
                }
                if (nextCursor != null) {
                    dataSource.replaceSyncCursor(nextCursor.value());
                }
                dataSource.markSynced();
                return deletedCount;
            } else if (succeeded > 0) {
                job.markPartialFailed(succeeded + failed, failed);
            } else {
                job.markFailed("전체 " + failed + "개 항목 수집 실패");
            }
            return null;
        });
        if (
            reconciliationMode == ConnectorReconciliationMode.FULL_SNAPSHOT
                && softDeletedDocumentCount != null
        ) {
            log.info(
                "전체 snapshot 문서 정리 완료: job={}, dataSource={}, softDeleted={}",
                jobId,
                sourceRun.source().id(),
                softDeletedDocumentCount
            );
        }
    }

    private void markFailed(UUID jobId, String errorMessage) {
        transactions.executeWithoutResult(status -> {
            IngestionJobEntity job = loadJob(jobId);
            job.markFailed(errorMessage);
        });
    }

    private IngestionJobEntity loadJob(UUID jobId) {
        return ingestionJobs.findById(jobId)
            .orElseThrow(() -> new IllegalArgumentException("수집 job을 찾을 수 없습니다: " + jobId));
    }

    private record SourceRunSnapshot(DataSourceSnapshot source, OffsetDateTime revision) {
    }
}
