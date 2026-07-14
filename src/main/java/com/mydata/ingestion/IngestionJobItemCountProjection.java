package com.mydata.ingestion;

import java.util.UUID;

public interface IngestionJobItemCountProjection {
    UUID getJobId();

    long getSucceededItemCount();

    long getFailedItemCount();
}
