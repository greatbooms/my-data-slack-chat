package com.mydata.ingestion;

import com.mydata.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(name = "ingestion_jobs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IngestionJobEntity extends BaseEntity {
    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "data_source_id", nullable = false)
    private UUID dataSourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, columnDefinition = "text")
    private IngestionTriggerType triggerType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "text")
    private IngestionJobStatus status;

    @Column(name = "requested_by_user_id")
    private UUID requestedByUserId;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    public static IngestionJobEntity pending(
        UUID workspaceId,
        UUID dataSourceId,
        IngestionTriggerType triggerType,
        UUID requestedByUserId
    ) {
        IngestionJobEntity job = new IngestionJobEntity();
        job.workspaceId = workspaceId;
        job.dataSourceId = dataSourceId;
        job.triggerType = triggerType;
        job.status = IngestionJobStatus.PENDING;
        job.requestedByUserId = requestedByUserId;
        return job;
    }

    public void markRunning() {
        status = IngestionJobStatus.RUNNING;
        startedAt = OffsetDateTime.now();
        finishedAt = null;
        errorMessage = null;
    }

    public void markSucceeded() {
        status = IngestionJobStatus.SUCCEEDED;
        finishedAt = OffsetDateTime.now();
        errorMessage = null;
    }

    public void markFailed(String errorMessage) {
        status = IngestionJobStatus.FAILED;
        finishedAt = OffsetDateTime.now();
        this.errorMessage = errorMessage;
    }
}
