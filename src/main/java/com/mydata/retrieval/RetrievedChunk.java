package com.mydata.retrieval;

import java.util.UUID;

public record RetrievedChunk(
    UUID chunkId,
    String content,
    String title,
    String uri,
    String sourceType,
    double distance
) {
}
