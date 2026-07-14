package com.mydata.connectors.notion;

import java.util.List;

public interface NotionClient {
    NotionApiClient.NotionPage retrievePage(String pageId);

    NotionApiClient.NotionDatabase retrieveDatabase(String databaseId);

    Batch<NotionApiClient.NotionPage> queryDataSourcePages(String dataSourceId, String startCursor);

    Batch<NotionApiClient.NotionBlock> listBlockChildren(String blockId, String startCursor);

    record Batch<T>(List<T> items, String nextCursor) {
        public Batch {
            items = items == null ? List.of() : List.copyOf(items);
            nextCursor = nextCursor == null || nextCursor.isBlank() ? null : nextCursor;
        }
    }
}
