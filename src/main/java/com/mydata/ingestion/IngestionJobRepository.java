package com.mydata.ingestion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface IngestionJobRepository extends JpaRepository<IngestionJobEntity, UUID> {
    List<IngestionJobEntity> findByDataSourceIdOrderByCreatedAtDesc(UUID dataSourceId);

    List<IngestionJobEntity> findTop10ByStatusOrderByCreatedAtAsc(IngestionJobStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE ingestion_jobs
        SET status = 'RUNNING',
            started_at = now(),
            finished_at = NULL,
            error_message = NULL
        WHERE id = :id
          AND status = 'PENDING'
        """, nativeQuery = true)
    int markPendingJobRunning(@Param("id") UUID id);
}
