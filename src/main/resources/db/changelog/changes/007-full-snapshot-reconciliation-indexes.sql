--liquibase formatted sql

--changeset eric:007-full-snapshot-reconciliation-indexes
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:0 SELECT count(*) FROM (SELECT data_source_id FROM ingestion_jobs WHERE status = 'RUNNING' GROUP BY data_source_id HAVING count(*) > 1) duplicate_running_data_sources
CREATE INDEX IF NOT EXISTS idx_ingestion_job_items_job_succeeded_document
    ON ingestion_job_items(job_id, document_id)
    WHERE status = 'SUCCEEDED'
      AND document_id IS NOT NULL;

CREATE UNIQUE INDEX uq_ingestion_jobs_running_data_source
    ON ingestion_jobs(data_source_id)
    WHERE status = 'RUNNING';
