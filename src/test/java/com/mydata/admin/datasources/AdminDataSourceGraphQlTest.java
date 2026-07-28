package com.mydata.admin.datasources;

import com.mydata.auth.Permission;
import com.mydata.auth.PrincipalKeys;
import com.mydata.datasources.DataSourceEntity;
import com.mydata.datasources.DataSourceRepository;
import com.mydata.datasources.DataSourceStatus;
import com.mydata.datasources.DataSourceType;
import com.mydata.datasources.SyncMode;
import com.mydata.documents.DocumentChunkEntity;
import com.mydata.documents.ExternalDocumentEntity;
import com.mydata.documents.ExternalDocumentRepository;
import com.mydata.embeddings.EmbeddingMigrationService;
import com.mydata.support.PostgresIntegrationTest;
import com.mydata.users.UserEntity;
import com.mydata.users.UserRepository;
import com.mydata.users.UserRole;
import com.mydata.users.UserStatus;
import com.mydata.workspaces.WorkspaceEntity;
import com.mydata.workspaces.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class AdminDataSourceGraphQlTest extends PostgresIntegrationTest {
    MockMvc mockMvc;
    @Autowired WebApplicationContext webApplicationContext;
    @Autowired UserRepository users;
    @Autowired WorkspaceRepository workspaces;
    @Autowired DataSourceRepository dataSources;
    @Autowired ExternalDocumentRepository documents;
    @Autowired EmbeddingMigrationService embeddingMigration;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .apply(springSecurity())
            .build();
    }

    @Test
    void managesDataSourcesThroughGraphQl() throws Exception {
        String suffix = UUID.randomUUID().toString();
        UserEntity owner = users.save(UserEntity.create("owner-" + suffix + "@example.com", "Owner"));
        WorkspaceEntity workspace = workspaces.save(WorkspaceEntity.create(owner.getId(), "Personal"));
        MockHttpSession adminSession = loginAs("admin-" + suffix + "@example.com");
        int initialDataSourceCount = dataSources.findByDeletedAtIsNullOrderByCreatedAtDesc().size();

        MvcResult createResult = graphQl(adminSession, """
            mutation {
              createDataSource(input: {
                workspaceId: "%s",
                ownerUserId: "%s",
                type: LOCAL_TEXT,
                name: "Local notes",
                visibility: PRIVATE,
                syncMode: MANUAL
              }) {
                id
                workspaceId
                ownerUserId
                name
                type
                visibility
                syncMode
              }
            }
            """.formatted(workspace.getId(), owner.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.createDataSource.workspaceId").value(workspace.getId().toString()))
            .andExpect(jsonPath("$.data.createDataSource.ownerUserId").value(owner.getId().toString()))
            .andExpect(jsonPath("$.data.createDataSource.name").value("Local notes"))
            .andExpect(jsonPath("$.data.createDataSource.type").value("LOCAL_TEXT"))
            .andExpect(jsonPath("$.data.createDataSource.visibility").value("PRIVATE"))
            .andExpect(jsonPath("$.data.createDataSource.syncMode").value("MANUAL"))
            .andReturn();

        String dataSourceId = JsonPaths.readString(createResult, "$.data.createDataSource.id");
        assertPolicy(dataSourceId, PrincipalKeys.user(owner.getId()));

        graphQl(adminSession, """
            mutation {
              updateDataSource(id: "%s", input: {
                name: "Workspace notes",
                visibility: WORKSPACE
              }) {
                name
                visibility
              }
            }
            """.formatted(dataSourceId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.updateDataSource.name").value("Workspace notes"))
            .andExpect(jsonPath("$.data.updateDataSource.visibility").value("WORKSPACE"));
        assertPolicy(dataSourceId, PrincipalKeys.workspace(workspace.getId()));

        graphQl(adminSession, """
            mutation {
              requestDataSourceSync(id: "%s") {
                status
                triggerType
              }
            }
            """.formatted(dataSourceId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.requestDataSourceSync.status").value("PENDING"))
            .andExpect(jsonPath("$.data.requestDataSourceSync.triggerType").value("MANUAL"));

        graphQl(adminSession, """
            query {
              dataSources {
                totalCount
                items {
                  name
                }
              }
              ingestionJobs(dataSourceId: "%s") {
                status
                succeededItemCount
                failedItemCount
              }
            }
            """.formatted(dataSourceId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.dataSources.totalCount").value(initialDataSourceCount + 1))
            .andExpect(jsonPath("$.data.dataSources.items[*].name").value(hasItem("Workspace notes")))
            .andExpect(jsonPath("$.data.ingestionJobs[*].status").value(hasItem("PENDING")))
            .andExpect(jsonPath("$.data.ingestionJobs[0].succeededItemCount").value(0))
            .andExpect(jsonPath("$.data.ingestionJobs[0].failedItemCount").value(0));

        graphQl(adminSession, """
            mutation {
              softDeleteDataSource(id: "%s") {
                deletedAt
              }
            }
            """.formatted(dataSourceId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.softDeleteDataSource.deletedAt").isNotEmpty());
        assertNoPolicy(dataSourceId);

        graphQl(adminSession, """
            query {
              dataSources {
                items {
                  name
                }
              }
            }
            """)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.dataSources.items[*].name").value(not(hasItem("Workspace notes"))));
    }

    @Test
    void createsAndUpdatesDataSourceSyncCron() throws Exception {
        String suffix = UUID.randomUUID().toString();
        UserEntity owner = users.save(UserEntity.create("cron-owner-" + suffix + "@example.com", "Owner"));
        WorkspaceEntity workspace = workspaces.save(WorkspaceEntity.create(owner.getId(), "Personal"));
        MockHttpSession adminSession = loginAs("cron-admin-" + suffix + "@example.com");

        MvcResult createResult = graphQl(adminSession, """
            mutation {
              createDataSource(input: {
                workspaceId: "%s",
                ownerUserId: "%s",
                type: LOCAL_TEXT,
                name: "Scheduled notes",
                visibility: PRIVATE,
                syncMode: SCHEDULED,
                syncCron: "0 0 * * * *"
              }) {
                id
                syncCron
              }
            }
            """.formatted(workspace.getId(), owner.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.createDataSource.syncCron").value("0 0 * * * *"))
            .andReturn();

        String dataSourceId = JsonPaths.readString(createResult, "$.data.createDataSource.id");
        assertThat(dataSources.findById(UUID.fromString(dataSourceId)).orElseThrow().getSyncCron())
            .isEqualTo("0 0 * * * *");

        graphQl(adminSession, """
            mutation {
              updateDataSource(id: "%s", input: {
                syncCron: "0 */15 * * * *"
              }) {
                syncCron
              }
            }
            """.formatted(dataSourceId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.updateDataSource.syncCron").value("0 */15 * * * *"));

        assertThat(dataSources.findById(UUID.fromString(dataSourceId)).orElseThrow().getSyncCron())
            .isEqualTo("0 */15 * * * *");
    }

    @Test
    void rejectsInvalidDataSourceSyncCron() throws Exception {
        String suffix = UUID.randomUUID().toString();
        UserEntity owner = users.save(UserEntity.create("invalid-cron-owner-" + suffix + "@example.com", "Owner"));
        WorkspaceEntity workspace = workspaces.save(WorkspaceEntity.create(owner.getId(), "Personal"));
        MockHttpSession adminSession = loginAs("invalid-cron-admin-" + suffix + "@example.com");

        graphQl(adminSession, """
            mutation {
              createDataSource(input: {
                workspaceId: "%s",
                ownerUserId: "%s",
                type: LOCAL_TEXT,
                name: "Invalid schedule",
                visibility: PRIVATE,
                syncMode: SCHEDULED,
                syncCron: "not-a-cron"
              }) {
                id
              }
            }
            """.formatted(workspace.getId(), owner.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.errors[0].message").value("sync_cron 형식이 올바르지 않습니다"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "https://www.notion.so/greatbooms/Project-Wiki-248104cd477e80fdb757e945d38000bd?pvs=4",
        "248104cd477e80fdb757e945d38000bd",
        "248104cd-477e-80fd-b757-e945d38000bd"
    })
    void createsNotionDataSourceWithRootPageLinkOrIdConfig(String notionRootPageInput) throws Exception {
        String suffix = UUID.randomUUID().toString();
        UserEntity owner = users.save(UserEntity.create("notion-owner-" + suffix + "@example.com", "Owner"));
        WorkspaceEntity workspace = workspaces.save(WorkspaceEntity.create(owner.getId(), "Personal"));
        MockHttpSession adminSession = loginAs("notion-admin-" + suffix + "@example.com");

        MvcResult createResult = graphQl(adminSession, """
            mutation {
              createDataSource(input: {
                workspaceId: "%s",
                ownerUserId: "%s",
                type: NOTION,
                name: "Notion wiki",
                visibility: WORKSPACE,
                syncMode: MANUAL,
                notionRootPageId: "%s"
              }) {
                id
                type
                notionRootPageId
              }
            }
            """.formatted(workspace.getId(), owner.getId(), notionRootPageInput))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.createDataSource.type").value("NOTION"))
            .andExpect(jsonPath("$.data.createDataSource.notionRootPageId")
                .value("248104cd-477e-80fd-b757-e945d38000bd"))
            .andReturn();

        String dataSourceId = JsonPaths.readString(createResult, "$.data.createDataSource.id");
        assertThat(dataSources.findById(UUID.fromString(dataSourceId)).orElseThrow()
            .configValue("notionRootPageId"))
            .isEqualTo("248104cd-477e-80fd-b757-e945d38000bd");
        assertPolicy(dataSourceId, PrincipalKeys.workspace(workspace.getId()));
    }

    @Test
    void rejectsNotionDataSourceWithInvalidRootPageLink() throws Exception {
        String suffix = UUID.randomUUID().toString();
        UserEntity owner = users.save(UserEntity.create("notion-invalid-owner-" + suffix + "@example.com", "Owner"));
        WorkspaceEntity workspace = workspaces.save(WorkspaceEntity.create(owner.getId(), "Personal"));
        MockHttpSession adminSession = loginAs("notion-invalid-admin-" + suffix + "@example.com");
        int initialDataSourceCount = dataSources.findByDeletedAtIsNullOrderByCreatedAtDesc().size();

        graphQl(adminSession, """
            mutation {
              createDataSource(input: {
                workspaceId: "%s",
                ownerUserId: "%s",
                type: NOTION,
                name: "Invalid Notion page",
                visibility: WORKSPACE,
                syncMode: MANUAL,
                notionRootPageId: "https://www.notion.so/greatbooms/not-a-page-id"
              }) {
                id
              }
            }
            """.formatted(workspace.getId(), owner.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.errors[0].message")
                .value("notionRootPageId 형식이 올바르지 않습니다"));

        assertThat(dataSources.findByDeletedAtIsNullOrderByCreatedAtDesc())
            .hasSize(initialDataSourceCount);
    }

    @Test
    void updatesNotionRootPageConfigFromLink() throws Exception {
        String suffix = UUID.randomUUID().toString();
        UserEntity owner = users.save(UserEntity.create("notion-update-owner-" + suffix + "@example.com", "Owner"));
        WorkspaceEntity workspace = workspaces.save(WorkspaceEntity.create(owner.getId(), "Personal"));
        MockHttpSession adminSession = loginAs("notion-update-admin-" + suffix + "@example.com");

        MvcResult createResult = graphQl(adminSession, """
            mutation {
              createDataSource(input: {
                workspaceId: "%s",
                ownerUserId: "%s",
                type: NOTION,
                name: "Notion wiki",
                visibility: WORKSPACE,
                syncMode: MANUAL,
                notionRootPageId: "248104cd477e80fdb757e945d38000bd"
              }) {
                id
              }
            }
            """.formatted(workspace.getId(), owner.getId()))
            .andExpect(status().isOk())
            .andReturn();
        String dataSourceId = JsonPaths.readString(createResult, "$.data.createDataSource.id");

        graphQl(adminSession, """
            mutation {
              updateDataSource(id: "%s", input: {
                notionRootPageId: "https://www.notion.so/greatbooms/Updated-0123456789abcdef0123456789abcdef?pvs=4"
              }) {
                notionRootPageId
              }
            }
            """.formatted(dataSourceId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.updateDataSource.notionRootPageId")
                .value("01234567-89ab-cdef-0123-456789abcdef"));

        assertThat(dataSources.findById(UUID.fromString(dataSourceId)).orElseThrow()
            .configValue("notionRootPageId"))
            .isEqualTo("01234567-89ab-cdef-0123-456789abcdef");
    }

    @Test
    void createsNotionDataSourceWithDatabaseLinkConfig() throws Exception {
        String suffix = UUID.randomUUID().toString();
        UserEntity owner = users.save(UserEntity.create("notion-db-owner-" + suffix + "@example.com", "Owner"));
        WorkspaceEntity workspace = workspaces.save(WorkspaceEntity.create(owner.getId(), "Personal"));
        MockHttpSession adminSession = loginAs("notion-db-admin-" + suffix + "@example.com");

        MvcResult createResult = graphQl(adminSession, """
            mutation {
              createDataSource(input: {
                workspaceId: "%s",
                ownerUserId: "%s",
                type: NOTION,
                name: "Notion database",
                visibility: WORKSPACE,
                syncMode: MANUAL,
                notionDatabaseId: "https://www.notion.so/greatbooms/Roadmap-248104cd477e80fdb757e945d38000bd?v=248104cd477e80afbc30000bd28de8f9"
              }) {
                id
                type
                notionRootPageId
                notionDatabaseId
              }
            }
            """.formatted(workspace.getId(), owner.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.createDataSource.type").value("NOTION"))
            .andExpect(jsonPath("$.data.createDataSource.notionRootPageId").doesNotExist())
            .andExpect(jsonPath("$.data.createDataSource.notionDatabaseId").value("248104cd-477e-80fd-b757-e945d38000bd"))
            .andReturn();

        String dataSourceId = JsonPaths.readString(createResult, "$.data.createDataSource.id");
        assertThat(dataSources.findById(UUID.fromString(dataSourceId)).orElseThrow()
            .configValue("notionDatabaseId")).isEqualTo("248104cd-477e-80fd-b757-e945d38000bd");
        assertPolicy(dataSourceId, PrincipalKeys.workspace(workspace.getId()));
    }

    @Test
    void rejectsNotionDataSourceWithBothPageAndDatabaseConfig() throws Exception {
        String suffix = UUID.randomUUID().toString();
        UserEntity owner = users.save(UserEntity.create("notion-both-owner-" + suffix + "@example.com", "Owner"));
        WorkspaceEntity workspace = workspaces.save(WorkspaceEntity.create(owner.getId(), "Personal"));
        MockHttpSession adminSession = loginAs("notion-both-admin-" + suffix + "@example.com");

        graphQl(adminSession, """
            mutation {
              createDataSource(input: {
                workspaceId: "%s",
                ownerUserId: "%s",
                type: NOTION,
                name: "Invalid Notion",
                visibility: WORKSPACE,
                syncMode: MANUAL,
                notionRootPageId: "root-page-id",
                notionDatabaseId: "248104cd477e80fdb757e945d38000bd"
              }) {
                id
              }
            }
            """.formatted(workspace.getId(), owner.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.errors[0].message")
                .value("NOTION 데이터소스는 notionRootPageId 또는 notionDatabaseId 중 하나만 설정해야 합니다"));
    }

    @Test
    void rejectsNotionDataSourceWithoutPageOrDatabaseConfig() throws Exception {
        String suffix = UUID.randomUUID().toString();
        UserEntity owner = users.save(UserEntity.create("notion-empty-owner-" + suffix + "@example.com", "Owner"));
        WorkspaceEntity workspace = workspaces.save(WorkspaceEntity.create(owner.getId(), "Personal"));
        MockHttpSession adminSession = loginAs("notion-empty-admin-" + suffix + "@example.com");

        graphQl(adminSession, """
            mutation {
              createDataSource(input: {
                workspaceId: "%s",
                ownerUserId: "%s",
                type: NOTION,
                name: "Invalid Notion",
                visibility: WORKSPACE,
                syncMode: MANUAL
              }) {
                id
              }
            }
            """.formatted(workspace.getId(), owner.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.errors[0].message")
                .value("NOTION 데이터소스는 notionRootPageId 또는 notionDatabaseId 중 하나를 설정해야 합니다"));
    }

    @Test
    void createsGoogleDriveDataSourceWithFolderUrlConfig() throws Exception {
        String suffix = UUID.randomUUID().toString();
        UserEntity owner = users.save(UserEntity.create("drive-url-owner-" + suffix + "@example.com", "Owner"));
        WorkspaceEntity workspace = workspaces.save(WorkspaceEntity.create(owner.getId(), "Drive workspace"));
        MockHttpSession adminSession = loginAs("drive-url-admin-" + suffix + "@example.com");

        MvcResult createResult = graphQl(adminSession, """
            mutation {
              createDataSource(input: {
                workspaceId: "%s",
                ownerUserId: "%s",
                type: GOOGLE_DRIVE,
                name: "Drive folder",
                visibility: WORKSPACE,
                syncMode: MANUAL,
                driveFolderId: "https://drive.google.com/drive/folders/abc123XYZ_-45?usp=sharing"
              }) {
                id
                type
                driveFolderId
              }
            }
            """.formatted(workspace.getId(), owner.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.createDataSource.type").value("GOOGLE_DRIVE"))
            .andExpect(jsonPath("$.data.createDataSource.driveFolderId").value("abc123XYZ_-45"))
            .andReturn();

        String dataSourceId = JsonPaths.readString(createResult, "$.data.createDataSource.id");
        assertThat(dataSources.findById(UUID.fromString(dataSourceId)).orElseThrow()
            .configValue("driveFolderId")).isEqualTo("abc123XYZ_-45");
        assertPolicy(dataSourceId, PrincipalKeys.workspace(workspace.getId()));
    }

    @Test
    void createsGoogleDriveDataSourceWithRawFolderIdConfig() throws Exception {
        String suffix = UUID.randomUUID().toString();
        UserEntity owner = users.save(UserEntity.create("drive-id-owner-" + suffix + "@example.com", "Owner"));
        WorkspaceEntity workspace = workspaces.save(WorkspaceEntity.create(owner.getId(), "Drive workspace"));
        MockHttpSession adminSession = loginAs("drive-id-admin-" + suffix + "@example.com");

        MvcResult createResult = graphQl(adminSession, """
            mutation {
              createDataSource(input: {
                workspaceId: "%s",
                ownerUserId: "%s",
                type: GOOGLE_DRIVE,
                name: "Drive folder",
                visibility: WORKSPACE,
                syncMode: MANUAL,
                driveFolderId: "abc123XYZ_-45"
              }) {
                id
                driveFolderId
              }
            }
            """.formatted(workspace.getId(), owner.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.createDataSource.driveFolderId").value("abc123XYZ_-45"))
            .andReturn();

        String dataSourceId = JsonPaths.readString(createResult, "$.data.createDataSource.id");
        assertThat(dataSources.findById(UUID.fromString(dataSourceId)).orElseThrow()
            .configValue("driveFolderId")).isEqualTo("abc123XYZ_-45");
    }

    @Test
    void rejectsGoogleDriveDataSourceWithoutFolderId() throws Exception {
        String suffix = UUID.randomUUID().toString();
        UserEntity owner = users.save(UserEntity.create("drive-empty-owner-" + suffix + "@example.com", "Owner"));
        WorkspaceEntity workspace = workspaces.save(WorkspaceEntity.create(owner.getId(), "Drive workspace"));
        MockHttpSession adminSession = loginAs("drive-empty-admin-" + suffix + "@example.com");

        graphQl(adminSession, """
            mutation {
              createDataSource(input: {
                workspaceId: "%s",
                ownerUserId: "%s",
                type: GOOGLE_DRIVE,
                name: "Drive folder",
                visibility: WORKSPACE,
                syncMode: MANUAL
              }) {
                id
              }
            }
            """.formatted(workspace.getId(), owner.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.errors[0].message")
                .value("GOOGLE_DRIVE 데이터소스는 driveFolderId를 설정해야 합니다"));
    }

    @Test
    void rejectsDriveFolderIdUpdateForNotionDataSource() throws Exception {
        String suffix = UUID.randomUUID().toString();
        UserEntity owner = users.save(UserEntity.create("drive-notion-owner-" + suffix + "@example.com", "Owner"));
        WorkspaceEntity workspace = workspaces.save(WorkspaceEntity.create(owner.getId(), "Personal"));
        MockHttpSession adminSession = loginAs("drive-notion-admin-" + suffix + "@example.com");

        MvcResult createResult = graphQl(adminSession, """
            mutation {
              createDataSource(input: {
                workspaceId: "%s",
                ownerUserId: "%s",
                type: NOTION,
                name: "Notion wiki",
                visibility: WORKSPACE,
                syncMode: MANUAL,
                notionRootPageId: "248104cd477e80fdb757e945d38000bd"
              }) {
                id
              }
            }
            """.formatted(workspace.getId(), owner.getId()))
            .andExpect(status().isOk())
            .andReturn();
        String dataSourceId = JsonPaths.readString(createResult, "$.data.createDataSource.id");

        graphQl(adminSession, """
            mutation {
              updateDataSource(id: "%s", input: {
                driveFolderId: "abc123XYZ_-45"
              }) {
                driveFolderId
              }
            }
            """.formatted(dataSourceId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.errors[0].message")
                .value("driveFolderId는 GOOGLE_DRIVE 데이터소스에서만 설정할 수 있습니다"));
    }

    @Test
    void createsSlackDataSourceWithChannelConfig() throws Exception {
        String suffix = UUID.randomUUID().toString();
        UserEntity owner = users.save(UserEntity.create("slack-owner-" + suffix + "@example.com", "Owner"));
        WorkspaceEntity workspace = workspaces.save(WorkspaceEntity.create(owner.getId(), "Slack workspace"));
        MockHttpSession adminSession = loginAs("slack-admin-" + suffix + "@example.com");

        MvcResult createResult = graphQl(adminSession, """
            mutation {
              createDataSource(input: {
                workspaceId: "%s",
                ownerUserId: "%s",
                type: SLACK,
                name: "Slack channel",
                visibility: WORKSPACE,
                syncMode: MANUAL,
                slackChannelId: "C1234567890",
                slackWorkspaceUrl: "https://example.slack.com/"
              }) {
                id
                type
                slackChannelId
                slackWorkspaceUrl
              }
            }
            """.formatted(workspace.getId(), owner.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.createDataSource.type").value("SLACK"))
            .andExpect(jsonPath("$.data.createDataSource.slackChannelId").value("C1234567890"))
            .andExpect(jsonPath("$.data.createDataSource.slackWorkspaceUrl").value("https://example.slack.com"))
            .andReturn();

        String dataSourceId = JsonPaths.readString(createResult, "$.data.createDataSource.id");
        DataSourceEntity dataSource = dataSources.findById(UUID.fromString(dataSourceId)).orElseThrow();
        assertThat(dataSource.configValue("slackChannelId")).isEqualTo("C1234567890");
        assertThat(dataSource.configValue("slackWorkspaceUrl")).isEqualTo("https://example.slack.com");
        assertPolicy(dataSourceId, PrincipalKeys.workspace(workspace.getId()));
    }

    @Test
    void normalizesSlackWorkspaceUrlToOriginWhenFullSlackLinkIsProvided() throws Exception {
        String suffix = UUID.randomUUID().toString();
        UserEntity owner = users.save(UserEntity.create("slack-full-url-owner-" + suffix + "@example.com", "Owner"));
        WorkspaceEntity workspace = workspaces.save(WorkspaceEntity.create(owner.getId(), "Slack workspace"));
        MockHttpSession adminSession = loginAs("slack-full-url-admin-" + suffix + "@example.com");

        MvcResult createResult = graphQl(adminSession, """
            mutation {
              createDataSource(input: {
                workspaceId: "%s",
                ownerUserId: "%s",
                type: SLACK,
                name: "Slack channel",
                visibility: WORKSPACE,
                syncMode: MANUAL,
                slackChannelId: "C1234567890",
                slackWorkspaceUrl: "https://example.slack.com/archives/C1234567890/p1710000000000100?thread_ts=1710000000.000100"
              }) {
                id
                slackWorkspaceUrl
              }
            }
            """.formatted(workspace.getId(), owner.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.createDataSource.slackWorkspaceUrl").value("https://example.slack.com"))
            .andReturn();

        String dataSourceId = JsonPaths.readString(createResult, "$.data.createDataSource.id");
        DataSourceEntity dataSource = dataSources.findById(UUID.fromString(dataSourceId)).orElseThrow();
        assertThat(dataSource.configValue("slackWorkspaceUrl")).isEqualTo("https://example.slack.com");
    }

    @Test
    void rejectsSlackDataSourceWithoutChannelId() throws Exception {
        String suffix = UUID.randomUUID().toString();
        UserEntity owner = users.save(UserEntity.create("slack-missing-owner-" + suffix + "@example.com", "Owner"));
        WorkspaceEntity workspace = workspaces.save(WorkspaceEntity.create(owner.getId(), "Slack workspace"));
        MockHttpSession adminSession = loginAs("slack-missing-admin-" + suffix + "@example.com");

        graphQl(adminSession, """
            mutation {
              createDataSource(input: {
                workspaceId: "%s",
                ownerUserId: "%s",
                type: SLACK,
                name: "Slack channel",
                visibility: WORKSPACE,
                syncMode: MANUAL,
                slackChannelId: " "
              }) {
                id
              }
            }
            """.formatted(workspace.getId(), owner.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.errors[0].message").value("slackChannelId 값은 비어 있을 수 없습니다"));
    }

    @Test
    void rejectsSlackDataSourceWithInvalidWorkspaceUrl() throws Exception {
        String suffix = UUID.randomUUID().toString();
        UserEntity owner = users.save(UserEntity.create("slack-url-owner-" + suffix + "@example.com", "Owner"));
        WorkspaceEntity workspace = workspaces.save(WorkspaceEntity.create(owner.getId(), "Slack workspace"));
        MockHttpSession adminSession = loginAs("slack-url-admin-" + suffix + "@example.com");

        graphQl(adminSession, """
            mutation {
              createDataSource(input: {
                workspaceId: "%s",
                ownerUserId: "%s",
                type: SLACK,
                name: "Slack channel",
                visibility: WORKSPACE,
                syncMode: MANUAL,
                slackChannelId: "C1234567890",
                slackWorkspaceUrl: "example.slack.com"
              }) {
                id
              }
            }
            """.formatted(workspace.getId(), owner.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.errors[0].message").value("slackWorkspaceUrl 형식이 올바르지 않습니다"));
    }

    @Test
    void rejectsAppSlackUrlForWorkspaceUrl() throws Exception {
        String suffix = UUID.randomUUID().toString();
        UserEntity owner = users.save(UserEntity.create("slack-app-url-owner-" + suffix + "@example.com", "Owner"));
        WorkspaceEntity workspace = workspaces.save(WorkspaceEntity.create(owner.getId(), "Slack workspace"));
        MockHttpSession adminSession = loginAs("slack-app-url-admin-" + suffix + "@example.com");

        graphQl(adminSession, """
            mutation {
              createDataSource(input: {
                workspaceId: "%s",
                ownerUserId: "%s",
                type: SLACK,
                name: "Slack channel",
                visibility: WORKSPACE,
                syncMode: MANUAL,
                slackChannelId: "C1234567890",
                slackWorkspaceUrl: "https://app.slack.com/client/T123/C1234567890"
              }) {
                id
              }
            }
            """.formatted(workspace.getId(), owner.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.errors[0].message")
                .value("slackWorkspaceUrl에는 app.slack.com이 아닌 워크스페이스별 Slack URL을 입력해야 합니다"));
    }

    @Test
    void listsWorkspaceOptionsForDataSourceForm() throws Exception {
        String suffix = UUID.randomUUID().toString();
        UserEntity owner = users.save(UserEntity.create("workspace-owner-" + suffix + "@example.com", "Owner"));
        WorkspaceEntity workspace = workspaces.save(WorkspaceEntity.create(owner.getId(), "Personal"));
        MockHttpSession adminSession = loginAs("workspace-admin-" + suffix + "@example.com");

        graphQl(adminSession, """
            query {
              workspaces {
                totalCount
                items {
                  id
                  ownerUserId
                  name
                }
              }
            }
            """)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.workspaces.items[?(@.id == '%s')].name".formatted(workspace.getId()))
                .value(hasItem("Personal")))
            .andExpect(jsonPath("$.data.workspaces.items[?(@.id == '%s')].ownerUserId".formatted(workspace.getId()))
                .value(hasItem(owner.getId().toString())))
            .andExpect(jsonPath("$.data.workspaces.totalCount").isNumber());
    }

    @Test
    void excludesDataSourcesFromDeletedWorkspaces() throws Exception {
        String suffix = UUID.randomUUID().toString();
        UserEntity owner = users.save(UserEntity.create("archived-workspace-owner-" + suffix + "@example.com", "Owner"));
        WorkspaceEntity workspace = workspaces.save(WorkspaceEntity.create(owner.getId(), "Archived workspace"));
        DataSourceEntity dataSource = dataSources.save(DataSourceEntity.create(
            workspace.getId(),
            DataSourceType.LOCAL_TEXT,
            "Archived workspace notes",
            DataSourceStatus.ACTIVE,
            SyncMode.MANUAL
        ));
        workspace.markDeleted();
        workspaces.saveAndFlush(workspace);
        MockHttpSession adminSession = loginAs("archived-workspace-admin-" + suffix + "@example.com");

        graphQl(adminSession, """
            query {
              dataSources {
                items {
                  name
                }
              }
            }
            """)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.dataSources.items[*].name")
                .value(not(hasItem("Archived workspace notes"))));

        graphQl(adminSession, """
            mutation {
              requestDataSourceSync(id: "%s") {
                id
              }
            }
            """.formatted(dataSource.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.errors[0].message").value("데이터소스를 찾을 수 없습니다"));
    }

    @Test
    void reembedsDataSourceAndReportsCoverageThroughGraphQl() throws Exception {
        String suffix = UUID.randomUUID().toString();
        UserEntity owner = users.save(UserEntity.create("reembed-owner-" + suffix + "@example.com", "소유자"));
        WorkspaceEntity workspace = workspaces.save(WorkspaceEntity.create(owner.getId(), "재임베딩 워크스페이스"));
        DataSourceEntity dataSource = DataSourceEntity.create(
            workspace.getId(),
            DataSourceType.LOCAL_TEXT,
            "재임베딩 데이터소스",
            DataSourceStatus.ACTIVE,
            SyncMode.MANUAL
        );
        dataSource.assignOwner(owner.getId());
        dataSource = dataSources.saveAndFlush(dataSource);
        ExternalDocumentEntity document = ExternalDocumentEntity.create(
            workspace.getId(),
            dataSource.getId(),
            "reembed-document",
            DataSourceType.LOCAL_TEXT.name(),
            "재임베딩 문서",
            "reembed-hash"
        );
        document.addChunk(DocumentChunkEntity.create(document, 0, "관리자 재임베딩 청크 하나", null));
        document.addChunk(DocumentChunkEntity.create(document, 1, "관리자 재임베딩 청크 둘", null));
        documents.saveAndFlush(document);
        embeddingMigration.reembedLoop(dataSource.getId());
        MockHttpSession adminSession = loginAs("reembed-admin-" + suffix + "@example.com");

        graphQl(adminSession, """
            mutation {
              reembedDataSource(id: "%s") {
                running
                coverage {
                  model
                  totalChunks
                  coveredChunks
                }
              }
            }
            """.formatted(dataSource.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.reembedDataSource.running").isBoolean())
            .andExpect(jsonPath("$.data.reembedDataSource.coverage.model").value("deterministic-1536"))
            .andExpect(jsonPath("$.data.reembedDataSource.coverage.totalChunks").value(2))
            .andExpect(jsonPath("$.data.reembedDataSource.coverage.coveredChunks").value(2));

        graphQl(adminSession, """
            query {
              dataSources {
                items {
                  id
                  embeddingCoverage {
                    totalChunks
                    coveredChunks
                  }
                }
              }
            }
            """)
            .andExpect(status().isOk())
            .andExpect(jsonPath(
                "$.data.dataSources.items[?(@.id == '%s')].embeddingCoverage.totalChunks"
                    .formatted(dataSource.getId())
            ).value(hasItem(2)))
            .andExpect(jsonPath(
                "$.data.dataSources.items[?(@.id == '%s')].embeddingCoverage.coveredChunks"
                    .formatted(dataSource.getId())
            ).value(hasItem(2)));
    }

    @Test
    void rejectsReembedMutationWithInvalidDataSourceId() throws Exception {
        MockHttpSession adminSession = loginAs("invalid-reembed-admin-" + UUID.randomUUID() + "@example.com");

        graphQl(adminSession, """
            mutation {
              reembedDataSource(id: "not-a-uuid") {
                running
              }
            }
            """)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.errors[0]").exists());
    }

    private MockHttpSession loginAs(String email) throws Exception {
        UserEntity admin = UserEntity.create(email, "관리자");
        admin.changeRole(UserRole.ADMIN);
        admin.changeStatus(UserStatus.ACTIVE);
        admin.updatePasswordHash(passwordEncoder.encode("secret1234"));
        users.save(admin);

        MvcResult loginResult = mockMvc.perform(post("/admin/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "email": "%s", "password": "secret1234" }
                    """.formatted(email)))
            .andExpect(status().isOk())
            .andReturn();

        return (MockHttpSession) loginResult.getRequest().getSession(false);
    }

    private org.springframework.test.web.servlet.ResultActions graphQl(
        MockHttpSession session,
        String query
    ) throws Exception {
        return mockMvc.perform(post("/admin/graphql")
            .session(session)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                { "query": "%s" }
                """.formatted(escapeJson(query))));
    }

    private void assertPolicy(String dataSourceId, String principalKey) {
        Integer matchingPolicies = jdbcTemplate.queryForObject("""
            SELECT count(*)
            FROM data_source_access_policies
            WHERE data_source_id = ?::uuid
              AND principal_key = ?
              AND permission = ?
            """, Integer.class, dataSourceId, principalKey, Permission.READ.name());
        Integer totalPolicies = jdbcTemplate.queryForObject("""
            SELECT count(*)
            FROM data_source_access_policies
            WHERE data_source_id = ?::uuid
            """, Integer.class, dataSourceId);

        assertThat(matchingPolicies).isEqualTo(1);
        assertThat(totalPolicies).isEqualTo(1);
    }

    private void assertNoPolicy(String dataSourceId) {
        Integer totalPolicies = jdbcTemplate.queryForObject("""
            SELECT count(*)
            FROM data_source_access_policies
            WHERE data_source_id = ?::uuid
            """, Integer.class, dataSourceId);

        assertThat(totalPolicies).isZero();
    }

    private static String escapeJson(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", " ");
    }

    private static final class JsonPaths {
        private JsonPaths() {
        }

        static String readString(MvcResult result, String path) throws Exception {
            return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), path);
        }
    }
}
