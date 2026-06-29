package com.mydata.connectors.notion;

import java.util.List;

public interface NotionClient {
    NotionApiClient.NotionPage retrievePage(String pageId);

    List<NotionApiClient.NotionBlock> listBlockChildren(String blockId);
}
