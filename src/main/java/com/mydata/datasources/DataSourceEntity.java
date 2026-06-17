package com.mydata.datasources;

import com.mydata.common.domain.BaseEntity;
import com.mydata.common.json.JsonMaps;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnTransformer;

import java.util.UUID;

@Getter
@Entity
@Table(name = "data_sources")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DataSourceEntity extends BaseEntity {
    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "text")
    private DataSourceType type;

    @Column(nullable = false, columnDefinition = "text")
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "text")
    private DataSourceStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_mode", nullable = false, columnDefinition = "text")
    private SyncMode syncMode;

    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "sync_cursor_json", nullable = false, columnDefinition = "jsonb")
    private String syncCursorJson = JsonMaps.EMPTY_OBJECT;

    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "config_json", nullable = false, columnDefinition = "jsonb")
    private String configJson = JsonMaps.EMPTY_OBJECT;

    public static DataSourceEntity create(
        UUID workspaceId,
        DataSourceType type,
        String name,
        DataSourceStatus status,
        SyncMode syncMode
    ) {
        DataSourceEntity dataSource = new DataSourceEntity();
        dataSource.workspaceId = workspaceId;
        dataSource.type = type;
        dataSource.name = name;
        dataSource.status = status;
        dataSource.syncMode = syncMode;
        dataSource.syncCursorJson = JsonMaps.EMPTY_OBJECT;
        dataSource.configJson = JsonMaps.EMPTY_OBJECT;
        return dataSource;
    }
}
