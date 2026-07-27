package com.mydata.admin.datasources;

public record ReembedStatusPayload(boolean running, EmbeddingCoveragePayload coverage) {
}
