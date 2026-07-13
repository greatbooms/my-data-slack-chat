package com.mydata.ingestion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface IngestionJobItemRepository extends JpaRepository<IngestionJobItemEntity, UUID> {
    long countByJobIdAndStatus(UUID jobId, IngestionJobItemStatus status);

    List<IngestionJobItemEntity> findByJobIdOrderByProcessedAtAscIdAsc(UUID jobId);
}
