--liquibase formatted sql

--changeset eric:006-ingestion-job-item-indexes
CREATE INDEX IF NOT EXISTS idx_ingestion_job_items_job_status_processed_id
    ON ingestion_job_items(job_id, status, processed_at, id);

CREATE INDEX IF NOT EXISTS idx_ingestion_job_items_job_processed_id
    ON ingestion_job_items(job_id, processed_at, id);
