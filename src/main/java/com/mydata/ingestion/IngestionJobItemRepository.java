package com.mydata.ingestion;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface IngestionJobItemRepository extends JpaRepository<IngestionJobItemEntity, UUID> {
    long countByJobIdAndStatus(UUID jobId, IngestionJobItemStatus status);

    List<IngestionJobItemEntity> findByJobIdOrderByProcessedAtAscIdAsc(UUID jobId);

    @Query(value = """
        SELECT job_id AS "jobId",
               count(*) FILTER (WHERE status = 'SUCCEEDED') AS "succeededItemCount",
               count(*) FILTER (WHERE status = 'FAILED') AS "failedItemCount"
        FROM ingestion_job_items
        WHERE job_id IN (:jobIds)
        GROUP BY job_id
        """, nativeQuery = true)
    List<IngestionJobItemCountProjection> summarizeByJobIdIn(
        @Param("jobIds") Collection<UUID> jobIds
    );

    @Query("""
        SELECT item FROM IngestionJobItemEntity item
        WHERE item.jobId = :jobId
          AND (:status IS NULL OR item.status = :status)
          AND (
            CAST(:afterProcessedAt AS OffsetDateTime) IS NULL
            OR item.processedAt > :afterProcessedAt
            OR (item.processedAt = :afterProcessedAt AND item.id > :afterId)
          )
        ORDER BY item.processedAt ASC, item.id ASC
        """)
    List<IngestionJobItemEntity> findAdminPage(
        @Param("jobId") UUID jobId,
        @Param("status") IngestionJobItemStatus status,
        @Param("afterProcessedAt") OffsetDateTime afterProcessedAt,
        @Param("afterId") UUID afterId,
        Pageable pageable
    );
}
