package com.mydata.admin.datasources;

import com.mydata.auth.Permission;
import com.mydata.auth.PrincipalKeys;
import com.mydata.datasources.DataSourceRepository;
import com.mydata.support.PostgresIntegrationTest;
import com.mydata.users.UserEntity;
import com.mydata.users.UserRepository;
import com.mydata.users.UserRole;
import com.mydata.users.UserStatus;
import com.mydata.workspaces.WorkspaceEntity;
import com.mydata.workspaces.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
              }
            }
            """.formatted(dataSourceId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.dataSources.totalCount").value(initialDataSourceCount + 1))
            .andExpect(jsonPath("$.data.dataSources.items[*].name").value(hasItem("Workspace notes")))
            .andExpect(jsonPath("$.data.ingestionJobs[*].status").value(hasItem("PENDING")));

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
    void createsNotionDataSourceWithRootPageConfig() throws Exception {
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
                notionRootPageId: "root-page-id"
              }) {
                id
                type
                notionRootPageId
              }
            }
            """.formatted(workspace.getId(), owner.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.createDataSource.type").value("NOTION"))
            .andExpect(jsonPath("$.data.createDataSource.notionRootPageId").value("root-page-id"))
            .andReturn();

        String dataSourceId = JsonPaths.readString(createResult, "$.data.createDataSource.id");
        assertThat(dataSources.findById(UUID.fromString(dataSourceId)).orElseThrow()
            .configValue("notionRootPageId")).isEqualTo("root-page-id");
        assertPolicy(dataSourceId, PrincipalKeys.workspace(workspace.getId()));
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
