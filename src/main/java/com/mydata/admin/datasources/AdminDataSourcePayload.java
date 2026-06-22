package com.mydata.admin.datasources;

import com.mydata.datasources.DataSourceEntity;
import com.mydata.datasources.DataSourceStatus;
import com.mydata.datasources.DataSourceType;
import com.mydata.datasources.DataSourceVisibility;
import com.mydata.datasources.SyncMode;

import java.util.UUID;

public record AdminDataSourcePayload(
    UUID id,
    UUID workspaceId,
    UUID ownerUserId,
    DataSourceType type,
    String name,
    DataSourceStatus status,
    SyncMode syncMode,
    DataSourceVisibility visibility,
    String lastSyncedAt,
    String deletedAt
) {
    public static AdminDataSourcePayload from(DataSourceEntity dataSource) {
        String lastSyncedAt = dataSource.getLastSyncedAt() == null ? null : dataSource.getLastSyncedAt().toString();
        String deletedAt = dataSource.getDeletedAt() == null ? null : dataSource.getDeletedAt().toString();
        return new AdminDataSourcePayload(
            dataSource.getId(),
            dataSource.getWorkspaceId(),
            dataSource.getOwnerUserId(),
            dataSource.getType(),
            dataSource.getName(),
            dataSource.getStatus(),
            dataSource.getSyncMode(),
            dataSource.getVisibility(),
            lastSyncedAt,
            deletedAt
        );
    }
}
