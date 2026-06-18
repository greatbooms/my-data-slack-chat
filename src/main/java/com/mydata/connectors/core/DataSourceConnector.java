package com.mydata.connectors.core;

import com.mydata.datasources.DataSourceEntity;
import com.mydata.datasources.DataSourceType;

public interface DataSourceConnector {
    DataSourceType supports();

    SyncCursor fetchChanges(DataSourceEntity dataSource, SyncCursor cursor, DocumentHandler handler);
}
