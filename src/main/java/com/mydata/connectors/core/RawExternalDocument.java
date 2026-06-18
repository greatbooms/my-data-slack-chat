package com.mydata.connectors.core;

import com.mydata.datasources.DataSourceType;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record RawExternalDocument(
    String externalId,
    DataSourceType sourceType,
    String title,
    String uri,
    String mimeType,
    Instant externalCreatedAt,
    Instant externalUpdatedAt,
    String contentHash,
    Map<String, Object> metadata,
    RawContent content,
    List<RawAclEntry> aclEntries
) {
}
