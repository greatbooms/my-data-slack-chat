package com.mydata.connectors.core;

import com.mydata.datasources.DataSourceType;

public interface DataSourceConnector {
    DataSourceType supports();

    SyncCursor fetchChanges(DataSourceSnapshot dataSource, ConnectorEventSink sink);
}
