package com.mydata.admin.datasources;

import com.mydata.datasources.DataSourceStatus;
import com.mydata.datasources.DataSourceType;
import com.mydata.datasources.DataSourceVisibility;
import com.mydata.datasources.SyncMode;

public final class AdminDataSourceInputs {
    private AdminDataSourceInputs() {
    }

    public record CreateDataSourceInput(
        String workspaceId,
        String ownerUserId,
        DataSourceType type,
        String name,
        DataSourceVisibility visibility,
        SyncMode syncMode,
        String notionRootPageId
    ) {
    }

    public record UpdateDataSourceInput(
        String ownerUserId,
        String name,
        DataSourceStatus status,
        DataSourceVisibility visibility,
        SyncMode syncMode,
        String notionRootPageId
    ) {
    }
}
