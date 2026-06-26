package com.mydata.admin.workspaces;

import com.mydata.support.PostgresIntegrationTest;
import com.mydata.users.UserEntity;
import com.mydata.users.UserRepository;
import com.mydata.users.UserRole;
import com.mydata.users.UserStatus;
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

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class AdminWorkspaceGraphQlTest extends PostgresIntegrationTest {
    MockMvc mockMvc;
    @Autowired WebApplicationContext webApplicationContext;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .apply(springSecurity())
            .build();
    }

    @Test
    void managesWorkspacesThroughGraphQl() throws Exception {
        String suffix = UUID.randomUUID().toString();
        UserEntity owner = users.save(UserEntity.create("owner-" + suffix + "@example.com", "Owner"));
        UserEntity nextOwner = users.save(UserEntity.create("next-owner-" + suffix + "@example.com", "Next Owner"));
        MockHttpSession adminSession = loginAs("workspace-admin-" + suffix + "@example.com");

        MvcResult createResult = graphQl(adminSession, """
            mutation {
              createWorkspace(input: {
                ownerUserId: "%s",
                name: "Team A"
              }) {
                id
                ownerUserId
                name
                deletedAt
              }
            }
            """.formatted(owner.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.createWorkspace.ownerUserId").value(owner.getId().toString()))
            .andExpect(jsonPath("$.data.createWorkspace.name").value("Team A"))
            .andExpect(jsonPath("$.data.createWorkspace.deletedAt").doesNotExist())
            .andReturn();

        String workspaceId = JsonPaths.readString(createResult, "$.data.createWorkspace.id");

        graphQl(adminSession, """
            mutation {
              updateWorkspace(id: "%s", input: {
                ownerUserId: "%s",
                name: "Team B"
              }) {
                id
                ownerUserId
                name
              }
            }
            """.formatted(workspaceId, nextOwner.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.updateWorkspace.ownerUserId").value(nextOwner.getId().toString()))
            .andExpect(jsonPath("$.data.updateWorkspace.name").value("Team B"));

        graphQl(adminSession, """
            mutation {
              softDeleteWorkspace(id: "%s") {
                id
                deletedAt
              }
            }
            """.formatted(workspaceId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.softDeleteWorkspace.deletedAt").isNotEmpty());

        graphQl(adminSession, """
            query {
              workspaces {
                items {
                  id
                }
              }
            }
            """)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.workspaces.items[*].id").value(not(hasItem(workspaceId))));

        graphQl(adminSession, """
            query {
              workspaces(includeDeleted: true) {
                items {
                  id
                }
              }
            }
            """)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.workspaces.items[*].id").value(hasItem(workspaceId)));

        graphQl(adminSession, """
            mutation {
              restoreWorkspace(id: "%s") {
                id
                deletedAt
              }
            }
            """.formatted(workspaceId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.restoreWorkspace.deletedAt").doesNotExist());
    }

    @Test
    void rejectsDataSourceCreationForDeletedWorkspace() throws Exception {
        String suffix = UUID.randomUUID().toString();
        UserEntity owner = users.save(UserEntity.create("deleted-workspace-owner-" + suffix + "@example.com", "Owner"));
        MockHttpSession adminSession = loginAs("deleted-workspace-admin-" + suffix + "@example.com");
        UUID workspaceId = jdbcTemplate.queryForObject("""
            INSERT INTO workspaces (owner_user_id, name, deleted_at)
            VALUES (?, 'Deleted', now())
            RETURNING id
            """, UUID.class, owner.getId());

        graphQl(adminSession, """
            mutation {
              createDataSource(input: {
                workspaceId: "%s",
                ownerUserId: "%s",
                type: LOCAL_TEXT,
                name: "Should fail",
                visibility: PRIVATE,
                syncMode: MANUAL
              }) {
                id
              }
            }
            """.formatted(workspaceId, owner.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.errors[0].message").value("워크스페이스를 찾을 수 없습니다"));
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
