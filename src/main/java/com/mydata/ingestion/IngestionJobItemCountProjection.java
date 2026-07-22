package com.mydata.ingestion;

import java.util.UUID;

public interface IngestionJobItemCountProjection {
    UUID getJobId();

    long getSucceededItemCount();

    long getSkippedItemCount();

    long getFailedItemCount();
}
