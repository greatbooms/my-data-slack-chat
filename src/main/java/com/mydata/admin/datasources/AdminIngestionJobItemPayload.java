package com.mydata.admin.datasources;

import com.mydata.ingestion.IngestionJobItemEntity;
import com.mydata.ingestion.IngestionJobItemStatus;

import java.util.UUID;

public record AdminIngestionJobItemPayload(
    String externalId,
    UUID documentId,
    IngestionJobItemStatus status,
    String reason,
    String processedAt
) {
    public static AdminIngestionJobItemPayload from(IngestionJobItemEntity item) {
        return new AdminIngestionJobItemPayload(
            item.getExternalId(),
            item.getDocumentId(),
            item.getStatus(),
            item.getReason(),
            item.getProcessedAt().toString()
        );
    }
}
