package com.mydata.admin.datasources;

import java.util.List;

public record AdminIngestionJobItemPagePayload(
    List<AdminIngestionJobItemPayload> items,
    boolean hasNextPage,
    String endCursor
) {
}
