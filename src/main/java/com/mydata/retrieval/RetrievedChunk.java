package com.mydata.retrieval;

import com.mydata.common.json.JsonMaps;

import java.util.UUID;

public record RetrievedChunk(
    UUID chunkId,
    String content,
    String title,
    String uri,
    String sourceType,
    double distance,
    String externalCreatedAt,
    String documentMetadataJson
) {
    public RetrievedChunk(
        UUID chunkId,
        String content,
        String title,
        String uri,
        String sourceType,
        double distance
    ) {
        this(chunkId, content, title, uri, sourceType, distance, null, JsonMaps.EMPTY_OBJECT);
    }

    public RetrievedChunk(
        UUID chunkId,
        String content,
        String title,
        String uri,
        String sourceType,
        double distance,
        String documentMetadataJson
    ) {
        this(chunkId, content, title, uri, sourceType, distance, null, documentMetadataJson);
    }

    public RetrievedChunk {
        if (documentMetadataJson == null || documentMetadataJson.isBlank()) {
            documentMetadataJson = JsonMaps.EMPTY_OBJECT;
        }
    }
}
