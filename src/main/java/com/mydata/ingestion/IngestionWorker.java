package com.mydata.ingestion;

import com.mydata.connectors.core.DataSourceConnector;
import com.mydata.connectors.core.SyncCursor;
import com.mydata.datasources.DataSourceEntity;
import com.mydata.datasources.DataSourceRepository;
import com.mydata.datasources.DataSourceType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class IngestionWorker {
    private final IngestionJobRepository ingestionJobs;
    private final DataSourceRepository dataSources;
    private final IngestionPipelineService pipeline;
    private final Map<DataSourceType, DataSourceConnector> connectors;

    public IngestionWorker(
        IngestionJobRepository ingestionJobs,
        DataSourceRepository dataSources,
        IngestionPipelineService pipeline,
        List<DataSourceConnector> connectors
    ) {
        this.ingestionJobs = ingestionJobs;
        this.dataSources = dataSources;
        this.pipeline = pipeline;
        this.connectors = new EnumMap<>(DataSourceType.class);
        for (DataSourceConnector connector : connectors) {
            this.connectors.put(connector.supports(), connector);
        }
    }

    @Transactional
    public void run(UUID jobId) {
        IngestionJobEntity job = ingestionJobs.findById(jobId)
            .orElseThrow(() -> new IllegalArgumentException("Ingestion job not found: " + jobId));
        job.markRunning();
        try {
            DataSourceEntity dataSource = dataSources.findById(job.getDataSourceId())
                .orElseThrow(() -> new IllegalStateException("Data source not found: " + job.getDataSourceId()));
            DataSourceConnector connector = connectors.get(dataSource.getType());
            if (connector == null) {
                throw new IllegalStateException("No connector registered for " + dataSource.getType());
            }

            connector.fetchChanges(dataSource, new SyncCursor(Map.of()), rawDocument -> pipeline.ingest(dataSource, rawDocument));
            job.markSucceeded();
        } catch (RuntimeException exception) {
            job.markFailed(exception.getMessage());
        }
    }
}
