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
        UPDATE ingestion_jobs target
        SET status = 'RUNNING',
            started_at = now(),
            finished_at = NULL,
            error_message = NULL
        WHERE target.id = :id
          AND target.status = 'PENDING'
          AND NOT EXISTS (
              SELECT 1
              FROM ingestion_jobs running
              WHERE running.data_source_id = target.data_source_id
                AND running.status = 'RUNNING'
          )
        """, nativeQuery = true)
    int markPendingJobRunningIfDataSourceIdle(@Param("id") UUID id);
}
