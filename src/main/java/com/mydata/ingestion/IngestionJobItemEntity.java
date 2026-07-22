package com.mydata.ingestion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Getter
@Entity
@Table(name = "ingestion_job_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IngestionJobItemEntity {
    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "job_id", nullable = false, updatable = false)
    private UUID jobId;

    @Column(name = "external_id", columnDefinition = "text")
    private String externalId;

    @Column(name = "document_id")
    private UUID documentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "text")
    private IngestionJobItemStatus status;

    @Column(columnDefinition = "text")
    private String reason;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private OffsetDateTime processedAt = OffsetDateTime.now();

    public static IngestionJobItemEntity succeeded(UUID jobId, String externalId, UUID documentId) {
        IngestionJobItemEntity item = new IngestionJobItemEntity();
        item.jobId = jobId;
        item.externalId = externalId;
        item.documentId = Objects.requireNonNull(documentId);
        item.status = IngestionJobItemStatus.SUCCEEDED;
        return item;
    }

    public static IngestionJobItemEntity skipped(UUID jobId, String externalId, UUID documentId) {
        IngestionJobItemEntity item = new IngestionJobItemEntity();
        item.jobId = jobId;
        item.externalId = externalId;
        item.documentId = Objects.requireNonNull(documentId);
        item.status = IngestionJobItemStatus.SKIPPED;
        item.reason = null;
        return item;
    }

    public static IngestionJobItemEntity failed(UUID jobId, String externalId, String reason) {
        IngestionJobItemEntity item = new IngestionJobItemEntity();
        item.jobId = jobId;
        item.externalId = externalId;
        item.status = IngestionJobItemStatus.FAILED;
        item.reason = reason;
        return item;
    }
}
