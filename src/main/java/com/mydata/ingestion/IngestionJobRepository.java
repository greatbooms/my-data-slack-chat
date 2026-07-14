package com.mydata.ingestion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface IngestionJobRepository extends JpaRepository<IngestionJobEntity, UUID> {
    List<IngestionJobEntity> findByDataSourceIdOrderByCreatedAtDesc(UUID dataSourceId);

    @Query(value = """
        SELECT pending.*
        FROM ingestion_jobs pending
        WHERE pending.status = 'PENDING'
          AND NOT EXISTS (
              SELECT 1
              FROM ingestion_jobs running
              WHERE running.data_source_id = pending.data_source_id
                AND running.status = 'RUNNING'
          )
          AND pending.id = (
              SELECT oldest.id
              FROM ingestion_jobs oldest
              WHERE oldest.data_source_id = pending.data_source_id
                AND oldest.status = 'PENDING'
              ORDER BY oldest.created_at ASC, oldest.id ASC
              LIMIT 1
          )
        ORDER BY pending.created_at ASC, pending.id ASC
        LIMIT 10
        """, nativeQuery = true)
    List<IngestionJobEntity> findTop10RunnablePendingJobs();

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
