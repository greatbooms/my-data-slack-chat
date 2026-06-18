package com.mydata.ingestion;

import com.mydata.datasources.DataSourceEntity;
import com.mydata.datasources.DataSourceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class IngestionCommandService {
    private final DataSourceRepository dataSources;
    private final IngestionJobRepository ingestionJobs;

    public IngestionCommandService(DataSourceRepository dataSources, IngestionJobRepository ingestionJobs) {
        this.dataSources = dataSources;
        this.ingestionJobs = ingestionJobs;
    }

    @Transactional
    public IngestionJobEntity requestManualSync(UUID dataSourceId, UUID requestedByUserId) {
        DataSourceEntity dataSource = dataSources.findById(dataSourceId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "data source not found"));

        return ingestionJobs.save(IngestionJobEntity.pending(
            dataSource.getWorkspaceId(),
            dataSource.getId(),
            IngestionTriggerType.MANUAL,
            requestedByUserId
        ));
    }
}
