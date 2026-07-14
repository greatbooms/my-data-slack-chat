package com.mydata.connectors.core;

import com.mydata.datasources.DataSourceType;

public interface DataSourceConnector {
    DataSourceType supports();

    default ConnectorReconciliationMode reconciliationMode() {
        return ConnectorReconciliationMode.NONE;
    }

    SyncCursor fetchChanges(DataSourceSnapshot dataSource, ConnectorEventSink sink);
}
