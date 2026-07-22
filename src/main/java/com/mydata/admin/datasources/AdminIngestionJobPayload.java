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
    int succeededItemCount,
    int skippedItemCount,
    int failedItemCount,
    String startedAt,
    String finishedAt,
    String createdAt
) {
    public static AdminIngestionJobPayload from(
        IngestionJobEntity job,
        long succeededItemCount,
        long skippedItemCount,
        long failedItemCount
    ) {
        String startedAt = job.getStartedAt() == null ? null : job.getStartedAt().toString();
        String finishedAt = job.getFinishedAt() == null ? null : job.getFinishedAt().toString();
        return new AdminIngestionJobPayload(
            job.getId(),
            job.getWorkspaceId(),
            job.getDataSourceId(),
            job.getTriggerType(),
            job.getStatus(),
            job.getErrorMessage(),
            Math.toIntExact(succeededItemCount),
            Math.toIntExact(skippedItemCount),
            Math.toIntExact(failedItemCount),
            startedAt,
            finishedAt,
            job.getCreatedAt().toString()
        );
    }

    public static AdminIngestionJobPayload from(IngestionJobEntity job) {
        return from(job, 0, 0, 0);
    }
}
