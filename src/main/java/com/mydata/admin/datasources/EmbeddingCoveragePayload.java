package com.mydata.admin.datasources;

import com.mydata.embeddings.EmbeddingMigrationService;

public record EmbeddingCoveragePayload(String model, int totalChunks, int coveredChunks) {
    public static EmbeddingCoveragePayload from(EmbeddingMigrationService.EmbeddingCoverage coverage) {
        return new EmbeddingCoveragePayload(
            coverage.model(),
            Math.toIntExact(coverage.totalChunks()),
            Math.toIntExact(coverage.coveredChunks())
        );
    }
}
