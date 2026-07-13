package com.mydata.connectors.core;

import com.mydata.auth.PrincipalKeys;
import com.mydata.datasources.DataSourceEntity;
import com.mydata.datasources.DataSourceType;
import com.mydata.datasources.DataSourceVisibility;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record DataSourceSnapshot(
    UUID id,
    UUID workspaceId,
    UUID ownerUserId,
    DataSourceType type,
    DataSourceVisibility visibility,
    Map<String, Object> config,
    SyncCursor cursor
) {
    public DataSourceSnapshot {
        config = Map.copyOf(config);
        cursor = cursor == null ? new SyncCursor(Map.of()) : cursor;
    }

    public static DataSourceSnapshot from(DataSourceEntity source) {
        return new DataSourceSnapshot(
            source.getId(),
            source.getWorkspaceId(),
            source.getOwnerUserId(),
            source.getType(),
            source.getVisibility(),
            source.configValues(),
            new SyncCursor(source.syncCursorValue())
        );
    }

    public String configValue(String key) {
        Object value = config.get(key);
        return value instanceof String stringValue ? stringValue : null;
    }

    public String visibilityPrincipalKey() {
        return switch (visibility) {
            case PRIVATE -> PrincipalKeys.user(Objects.requireNonNull(ownerUserId));
            case WORKSPACE -> PrincipalKeys.workspace(workspaceId);
        };
    }
}
