package com.mydata.ingestion;

import com.mydata.connectors.core.DataSourceConnector;
import com.mydata.connectors.core.SyncCursor;
import com.mydata.datasources.DataSourceEntity;
import com.mydata.datasources.DataSourceRepository;
import com.mydata.datasources.DataSourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class IngestionWorker {
    private static final Logger log = LoggerFactory.getLogger(IngestionWorker.class);

    private final IngestionJobRepository ingestionJobs;
    private final DataSourceRepository dataSources;
    private final IngestionPipelineService pipeline;
    private final Map<DataSourceType, DataSourceConnector> connectors;
    private final TransactionTemplate transactions;

    public IngestionWorker(
        IngestionJobRepository ingestionJobs,
        DataSourceRepository dataSources,
        IngestionPipelineService pipeline,
        List<DataSourceConnector> connectors,
        TransactionTemplate transactions
    ) {
        this.ingestionJobs = ingestionJobs;
        this.dataSources = dataSources;
        this.pipeline = pipeline;
        this.transactions = transactions;
        this.connectors = new EnumMap<>(DataSourceType.class);
        for (DataSourceConnector connector : connectors) {
            this.connectors.put(connector.supports(), connector);
        }
    }

    public void run(UUID jobId) {
        log.info("수집 job 실행 시작: {}", jobId);
        markRunning(jobId);
        try {
            ingestAndMarkSucceeded(jobId);
            log.info("수집 job 성공: {}", jobId);
        } catch (RuntimeException exception) {
            log.warn("수집 job 실패: {}", jobId, exception);
            markFailed(jobId, exception.getMessage());
        }
    }

    private void markRunning(UUID jobId) {
        transactions.executeWithoutResult(status -> {
            IngestionJobEntity job = loadJob(jobId);
            job.markRunning();
        });
    }

    private void ingestAndMarkSucceeded(UUID jobId) {
        transactions.executeWithoutResult(status -> {
            IngestionJobEntity job = loadJob(jobId);
            DataSourceEntity dataSource = dataSources.findById(job.getDataSourceId())
                .orElseThrow(() -> new IllegalStateException("데이터소스를 찾을 수 없습니다: " + job.getDataSourceId()));
            DataSourceConnector connector = connectors.get(dataSource.getType());
            if (connector == null) {
                throw new IllegalStateException("등록된 커넥터가 없습니다: " + dataSource.getType());
            }

            connector.fetchChanges(dataSource, new SyncCursor(Map.of()), rawDocument -> pipeline.ingest(dataSource, rawDocument));
            job.markSucceeded();
        });
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
}
