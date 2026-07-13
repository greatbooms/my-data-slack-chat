package com.mydata.connectors.notion;

import java.util.List;
import java.util.function.Consumer;

public interface NotionClient {
    NotionApiClient.NotionPage retrievePage(String pageId);

    NotionApiClient.NotionDatabase retrieveDatabase(String databaseId);

    void queryDataSourcePages(
        String dataSourceId,
        Consumer<List<NotionApiClient.NotionPage>> batchConsumer
    );

    void listBlockChildren(
        String blockId,
        Consumer<List<NotionApiClient.NotionBlock>> batchConsumer
    );
}
