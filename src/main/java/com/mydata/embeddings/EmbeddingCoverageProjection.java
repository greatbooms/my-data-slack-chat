package com.mydata.embeddings;

public interface EmbeddingCoverageProjection {
    long getTotalChunks();

    long getCoveredChunks();
}
