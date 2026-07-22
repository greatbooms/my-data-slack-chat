package com.mydata.documents;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ExternalDocumentRepository extends JpaRepository<ExternalDocumentEntity, UUID> {
    Optional<ExternalDocumentEntity> findByDataSourceIdAndExternalId(UUID dataSourceId, String externalId);

    @Modifying(flushAutomatically = true)
    @Query(value = """
        UPDATE external_documents d
        SET deleted_at = now(),
            updated_at = now()
        FROM ingestion_jobs j
        WHERE j.id = :jobId
          AND j.status = 'SUCCEEDED'
          AND d.workspace_id = j.workspace_id
          AND d.data_source_id = j.data_source_id
          AND d.deleted_at IS NULL
          AND NOT EXISTS (
              SELECT 1
              FROM ingestion_job_items failed
              WHERE failed.job_id = j.id
                AND failed.status = 'FAILED'
          )
          AND NOT EXISTS (
              SELECT 1
              FROM ingestion_job_items seen
              WHERE seen.job_id = j.id
                AND seen.status IN ('SUCCEEDED', 'SKIPPED')
                AND seen.document_id = d.id
          )
        """, nativeQuery = true)
    int softDeleteUnseenForSucceededFullSnapshot(@Param("jobId") UUID jobId);
}
