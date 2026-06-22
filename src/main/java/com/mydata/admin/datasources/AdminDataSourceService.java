package com.mydata.admin.datasources;

import com.mydata.admin.datasources.AdminDataSourceInputs.CreateDataSourceInput;
import com.mydata.admin.datasources.AdminDataSourceInputs.UpdateDataSourceInput;
import com.mydata.auth.Permission;
import com.mydata.auth.PrincipalKeys;
import com.mydata.datasources.DataSourceEntity;
import com.mydata.datasources.DataSourceRepository;
import com.mydata.datasources.DataSourceStatus;
import com.mydata.datasources.DataSourceType;
import com.mydata.datasources.DataSourceVisibility;
import com.mydata.datasources.SyncMode;
import com.mydata.ingestion.IngestionCommandService;
import com.mydata.ingestion.IngestionJobEntity;
import com.mydata.ingestion.IngestionJobRepository;
import com.mydata.users.UserRepository;
import com.mydata.workspaces.WorkspaceRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AdminDataSourceService {
    private final DataSourceRepository dataSources;
    private final WorkspaceRepository workspaces;
    private final UserRepository users;
    private final IngestionCommandService ingestionCommands;
    private final IngestionJobRepository ingestionJobs;
    private final JdbcTemplate jdbcTemplate;

    public AdminDataSourceService(
        DataSourceRepository dataSources,
        WorkspaceRepository workspaces,
        UserRepository users,
        IngestionCommandService ingestionCommands,
        IngestionJobRepository ingestionJobs,
        JdbcTemplate jdbcTemplate
    ) {
        this.dataSources = dataSources;
        this.workspaces = workspaces;
        this.users = users;
        this.ingestionCommands = ingestionCommands;
        this.ingestionJobs = ingestionJobs;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public AdminDataSourcePagePayload listDataSources() {
        List<AdminDataSourcePayload> items = dataSources.findByDeletedAtIsNullOrderByCreatedAtDesc().stream()
            .map(AdminDataSourcePayload::from)
            .toList();
        return new AdminDataSourcePagePayload(items, items.size());
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public AdminDataSourcePayload findDataSource(String id) {
        return AdminDataSourcePayload.from(activeDataSource(id));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public List<AdminIngestionJobPayload> ingestionJobs(String dataSourceId, Integer first) {
        UUID parsedDataSourceId = parseId(dataSourceId, "dataSourceId");
        int limit = first == null || first < 1 ? 20 : first;
        return ingestionJobs.findByDataSourceIdOrderByCreatedAtDesc(parsedDataSourceId).stream()
            .limit(limit)
            .map(AdminIngestionJobPayload::from)
            .toList();
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public AdminDataSourcePayload createDataSource(CreateDataSourceInput input) {
        UUID workspaceId = parseId(input.workspaceId(), "workspaceId");
        UUID ownerUserId = parseId(input.ownerUserId(), "ownerUserId");
        workspaces.findById(workspaceId)
            .orElseThrow(() -> new IllegalArgumentException("워크스페이스를 찾을 수 없습니다"));
        users.findByIdAndDeletedAtIsNull(ownerUserId)
            .orElseThrow(() -> new IllegalArgumentException("소유자를 찾을 수 없습니다"));

        DataSourceEntity dataSource = DataSourceEntity.create(
            workspaceId,
            requireType(input.type()),
            requireText(input.name(), "name"),
            DataSourceStatus.ACTIVE,
            input.syncMode() == null ? SyncMode.MANUAL : input.syncMode()
        );
        dataSource.assignOwner(ownerUserId);
        dataSource.changeVisibility(input.visibility() == null ? DataSourceVisibility.PRIVATE : input.visibility());
        DataSourceEntity savedDataSource = dataSources.save(dataSource);
        rebuildPolicy(savedDataSource);
        return AdminDataSourcePayload.from(savedDataSource);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public AdminDataSourcePayload updateDataSource(String id, UpdateDataSourceInput input) {
        DataSourceEntity dataSource = activeDataSource(id);
        if (hasText(input.name())) {
            dataSource.rename(input.name());
        }
        if (input.status() != null) {
            dataSource.changeStatus(input.status());
        }
        if (input.syncMode() != null) {
            dataSource.changeSyncMode(input.syncMode());
        }
        if (hasText(input.ownerUserId())) {
            UUID ownerUserId = parseId(input.ownerUserId(), "ownerUserId");
            users.findByIdAndDeletedAtIsNull(ownerUserId)
                .orElseThrow(() -> new IllegalArgumentException("소유자를 찾을 수 없습니다"));
            dataSource.assignOwner(ownerUserId);
        }
        if (input.visibility() != null) {
            dataSource.changeVisibility(input.visibility());
        }
        rebuildPolicy(dataSource);
        return AdminDataSourcePayload.from(dataSource);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public AdminDataSourcePayload softDeleteDataSource(String id) {
        DataSourceEntity dataSource = activeDataSource(id);
        dataSource.markDeleted();
        removePolicies(dataSource);
        return AdminDataSourcePayload.from(dataSource);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public AdminIngestionJobPayload requestDataSourceSync(String id, UUID requestedByUserId) {
        DataSourceEntity dataSource = activeDataSource(id);
        IngestionJobEntity job = ingestionCommands.requestManualSync(dataSource.getId(), requestedByUserId);
        return AdminIngestionJobPayload.from(job);
    }

    private DataSourceEntity activeDataSource(String id) {
        return dataSources.findByIdAndDeletedAtIsNull(parseId(id, "dataSourceId"))
            .orElseThrow(() -> new IllegalArgumentException("데이터소스를 찾을 수 없습니다"));
    }

    private void rebuildPolicy(DataSourceEntity dataSource) {
        dataSources.flush();
        removePolicies(dataSource);
        String principalKey = switch (dataSource.getVisibility()) {
            case PRIVATE -> PrincipalKeys.user(dataSource.getOwnerUserId());
            case WORKSPACE -> PrincipalKeys.workspace(dataSource.getWorkspaceId());
        };
        jdbcTemplate.update("""
            INSERT INTO data_source_access_policies (data_source_id, principal_key, permission)
            VALUES (?, ?, ?)
            """, dataSource.getId(), principalKey, Permission.READ.name());
    }

    private void removePolicies(DataSourceEntity dataSource) {
        jdbcTemplate.update("DELETE FROM data_source_access_policies WHERE data_source_id = ?", dataSource.getId());
    }

    private static DataSourceType requireType(DataSourceType type) {
        if (type == null) {
            throw new IllegalArgumentException("type 값은 비어 있을 수 없습니다");
        }
        if (type != DataSourceType.LOCAL_TEXT) {
            throw new IllegalArgumentException("현재 관리자 화면에서는 LOCAL_TEXT 데이터소스만 만들 수 있습니다");
        }
        return type;
    }

    private static UUID parseId(String id, String fieldName) {
        try {
            return UUID.fromString(id);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(fieldName + " 형식이 올바르지 않습니다", exception);
        }
    }

    private static String requireText(String value, String fieldName) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(fieldName + " 값은 비어 있을 수 없습니다");
        }
        return value.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
