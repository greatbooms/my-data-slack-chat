package com.mydata.admin.datasources;

import com.mydata.ingestion.IngestionJobEntity;
import com.mydata.ingestion.IngestionJobStatus;
import com.mydata.ingestion.IngestionTriggerType;

import java.util.UUID;

public record AdminIngestionJobPayload(
    UUID id,
    UUID workspaceId,
    UUID dataSourceId,
    IngestionTriggerType triggerType,
    IngestionJobStatus status,
    String errorMessage,
    String startedAt,
    String finishedAt,
    String createdAt
) {
    public static AdminIngestionJobPayload from(IngestionJobEntity job) {
        String startedAt = job.getStartedAt() == null ? null : job.getStartedAt().toString();
        String finishedAt = job.getFinishedAt() == null ? null : job.getFinishedAt().toString();
        return new AdminIngestionJobPayload(
            job.getId(),
            job.getWorkspaceId(),
            job.getDataSourceId(),
            job.getTriggerType(),
            job.getStatus(),
            job.getErrorMessage(),
            startedAt,
            finishedAt,
            job.getCreatedAt().toString()
        );
    }
}
