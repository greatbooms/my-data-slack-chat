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

import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AdminDataSourceService {
    private static final String NOTION_ROOT_PAGE_ID_CONFIG_KEY = "notionRootPageId";
    private static final String NOTION_DATABASE_ID_CONFIG_KEY = "notionDatabaseId";
    private static final String SLACK_CHANNEL_ID_CONFIG_KEY = "slackChannelId";
    private static final String SLACK_WORKSPACE_URL_CONFIG_KEY = "slackWorkspaceUrl";
    private static final Pattern NOTION_ID_PATTERN = Pattern.compile(
        "(?i)([0-9a-f]{32}|[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})"
    );

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
        List<AdminDataSourcePayload> items = dataSources.findActiveOrderByCreatedAtDesc().stream()
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
        workspaces.findByIdAndDeletedAtIsNull(workspaceId)
            .orElseThrow(() -> new IllegalArgumentException("워크스페이스를 찾을 수 없습니다"));
        users.findByIdAndDeletedAtIsNull(ownerUserId)
            .orElseThrow(() -> new IllegalArgumentException("소유자를 찾을 수 없습니다"));

        DataSourceType type = requireType(input.type());
        DataSourceEntity dataSource = DataSourceEntity.create(
            workspaceId,
            type,
            requireText(input.name(), "name"),
            DataSourceStatus.ACTIVE,
            input.syncMode() == null ? SyncMode.MANUAL : input.syncMode()
        );
        dataSource.assignOwner(ownerUserId);
        dataSource.changeVisibility(input.visibility() == null ? DataSourceVisibility.PRIVATE : input.visibility());
        applyCreateConfig(dataSource, input);
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
        applyUpdateConfig(dataSource, input);
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
        return dataSources.findActiveById(parseId(id, "dataSourceId"))
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

    private static void applyCreateConfig(DataSourceEntity dataSource, CreateDataSourceInput input) {
        if (dataSource.getType() == DataSourceType.NOTION) {
            applyNotionConfig(dataSource, input.notionRootPageId(), input.notionDatabaseId());
        }
        if (dataSource.getType() == DataSourceType.SLACK) {
            dataSource.putConfig(
                SLACK_CHANNEL_ID_CONFIG_KEY,
                requireText(input.slackChannelId(), "slackChannelId")
            );
            putOptionalSlackWorkspaceUrl(dataSource, input.slackWorkspaceUrl());
        }
    }

    private static void applyUpdateConfig(DataSourceEntity dataSource, UpdateDataSourceInput input) {
        if (input.notionRootPageId() != null || input.notionDatabaseId() != null) {
            if (dataSource.getType() != DataSourceType.NOTION) {
                throw new IllegalArgumentException("Notion 설정은 NOTION 데이터소스에서만 설정할 수 있습니다");
            }

            applyNotionConfig(dataSource, input.notionRootPageId(), input.notionDatabaseId());
        }

        if (input.slackChannelId() != null) {
            if (dataSource.getType() != DataSourceType.SLACK) {
                throw new IllegalArgumentException("slackChannelId는 SLACK 데이터소스에서만 설정할 수 있습니다");
            }

            dataSource.putConfig(
                SLACK_CHANNEL_ID_CONFIG_KEY,
                requireText(input.slackChannelId(), "slackChannelId")
            );
        }

        if (input.slackWorkspaceUrl() != null) {
            if (dataSource.getType() != DataSourceType.SLACK) {
                throw new IllegalArgumentException("slackWorkspaceUrl은 SLACK 데이터소스에서만 설정할 수 있습니다");
            }

            putOptionalSlackWorkspaceUrl(dataSource, input.slackWorkspaceUrl());
        }
    }

    private static void putOptionalSlackWorkspaceUrl(DataSourceEntity dataSource, String value) {
        if (!hasText(value)) {
            dataSource.putConfig(SLACK_WORKSPACE_URL_CONFIG_KEY, "");
            return;
        }

        dataSource.putConfig(SLACK_WORKSPACE_URL_CONFIG_KEY, normalizeHttpUrl(value, "slackWorkspaceUrl"));
    }

    private static void applyNotionConfig(DataSourceEntity dataSource, String rootPageId, String databaseId) {
        boolean hasRootPageId = hasText(rootPageId);
        boolean hasDatabaseId = hasText(databaseId);
        if (!hasRootPageId && !hasDatabaseId) {
            throw new IllegalArgumentException("NOTION 데이터소스는 notionRootPageId 또는 notionDatabaseId 중 하나를 설정해야 합니다");
        }
        if (hasRootPageId && hasDatabaseId) {
            throw new IllegalArgumentException("NOTION 데이터소스는 notionRootPageId 또는 notionDatabaseId 중 하나만 설정해야 합니다");
        }

        if (hasRootPageId) {
            dataSource.putConfig(NOTION_ROOT_PAGE_ID_CONFIG_KEY, requireText(rootPageId, "notionRootPageId"));
            dataSource.putConfig(NOTION_DATABASE_ID_CONFIG_KEY, "");
            return;
        }

        dataSource.putConfig(NOTION_ROOT_PAGE_ID_CONFIG_KEY, "");
        dataSource.putConfig(NOTION_DATABASE_ID_CONFIG_KEY, normalizeNotionDatabaseId(databaseId));
    }

    private static DataSourceType requireType(DataSourceType type) {
        if (type == null) {
            throw new IllegalArgumentException("type 값은 비어 있을 수 없습니다");
        }
        if (type != DataSourceType.LOCAL_TEXT && type != DataSourceType.NOTION && type != DataSourceType.SLACK) {
            throw new IllegalArgumentException("현재 관리자 화면에서는 LOCAL_TEXT, NOTION 또는 SLACK 데이터소스만 만들 수 있습니다");
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

    private static String normalizeHttpUrl(String value, String fieldName) {
        URI uri;
        try {
            uri = URI.create(value.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(fieldName + " 형식이 올바르지 않습니다", exception);
        }

        String scheme = uri.getScheme();
        if (uri.getHost() == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException(fieldName + " 형식이 올바르지 않습니다");
        }
        if ("app.slack.com".equalsIgnoreCase(uri.getHost())) {
            throw new IllegalArgumentException(fieldName + "에는 app.slack.com이 아닌 워크스페이스별 Slack URL을 입력해야 합니다");
        }

        try {
            URI origin = new URI(scheme.toLowerCase(), null, uri.getHost().toLowerCase(), uri.getPort(), null, null, null);
            return origin.toString();
        } catch (Exception exception) {
            throw new IllegalArgumentException(fieldName + " 형식이 올바르지 않습니다", exception);
        }
    }

    private static String normalizeNotionDatabaseId(String value) {
        String trimmed = requireText(value, "notionDatabaseId");
        String candidateSource = trimmed;
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            URI uri;
            try {
                uri = URI.create(trimmed);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("notionDatabaseId 형식이 올바르지 않습니다", exception);
            }
            candidateSource = uri.getPath() == null ? "" : uri.getPath();
        }

        Matcher matcher = NOTION_ID_PATTERN.matcher(candidateSource);
        String candidate = null;
        while (matcher.find()) {
            candidate = matcher.group(1);
        }
        if (candidate == null) {
            throw new IllegalArgumentException("notionDatabaseId 형식이 올바르지 않습니다");
        }

        String compact = candidate.replace("-", "").toLowerCase();
        return compact.substring(0, 8)
            + "-" + compact.substring(8, 12)
            + "-" + compact.substring(12, 16)
            + "-" + compact.substring(16, 20)
            + "-" + compact.substring(20);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
