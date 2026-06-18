package com.mydata.ingestion;

public enum IngestionJobStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    PARTIAL_FAILED,
    FAILED,
    CANCELLED
}
