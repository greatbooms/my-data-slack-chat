package com.mydata.admin.externalidentities;

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

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class AdminExternalIdentityGraphQlTest extends PostgresIntegrationTest {
    MockMvc mockMvc;
    @Autowired WebApplicationContext webApplicationContext;
    @Autowired UserRepository users;
    @Autowired WorkspaceRepository workspaces;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .apply(springSecurity())
            .build();
    }

    @Test
    void managesSlackExternalIdentitiesThroughGraphQl() throws Exception {
        String suffix = UUID.randomUUID().toString();
        UserEntity firstUser = users.save(UserEntity.create("slack-user-" + suffix + "@example.com", "Slack User"));
        UserEntity nextUser = users.save(UserEntity.create("slack-next-" + suffix + "@example.com", "Slack Next"));
        WorkspaceEntity firstWorkspace = workspaces.save(WorkspaceEntity.create(firstUser.getId(), "Slack workspace"));
        WorkspaceEntity nextWorkspace = workspaces.save(WorkspaceEntity.create(nextUser.getId(), "Next workspace"));
        MockHttpSession adminSession = loginAs("identity-admin-" + suffix + "@example.com");

        MvcResult createResult = graphQl(adminSession, """
            mutation {
              createExternalIdentity(input: {
                provider: SLACK,
                workspaceId: "%s",
                userId: "%s",
                externalWorkspaceId: "T123",
                externalUserId: "U123",
                email: "slack@example.com",
                displayName: "Slack Person"
              }) {
                id
                provider
                workspaceId
                userId
                externalWorkspaceId
                externalUserId
                email
                displayName
                principalKey
              }
            }
            """.formatted(firstWorkspace.getId(), firstUser.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.createExternalIdentity.provider").value("SLACK"))
            .andExpect(jsonPath("$.data.createExternalIdentity.workspaceId").value(firstWorkspace.getId().toString()))
            .andExpect(jsonPath("$.data.createExternalIdentity.userId").value(firstUser.getId().toString()))
            .andExpect(jsonPath("$.data.createExternalIdentity.externalWorkspaceId").value("T123"))
            .andExpect(jsonPath("$.data.createExternalIdentity.externalUserId").value("U123"))
            .andExpect(jsonPath("$.data.createExternalIdentity.email").value("slack@example.com"))
            .andExpect(jsonPath("$.data.createExternalIdentity.displayName").value("Slack Person"))
            .andExpect(jsonPath("$.data.createExternalIdentity.principalKey").value("SLACK_USER:T123:U123"))
            .andReturn();

        String identityId = JsonPaths.readString(createResult, "$.data.createExternalIdentity.id");

        graphQl(adminSession, """
            mutation {
              updateExternalIdentity(id: "%s", input: {
                workspaceId: "%s",
                userId: "%s",
                externalWorkspaceId: "T999",
                externalUserId: "U999",
                email: "updated@example.com",
                displayName: "Updated Slack Person"
              }) {
                id
                workspaceId
                userId
                externalWorkspaceId
                externalUserId
                email
                displayName
                principalKey
              }
            }
            """.formatted(identityId, nextWorkspace.getId(), nextUser.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.updateExternalIdentity.workspaceId").value(nextWorkspace.getId().toString()))
            .andExpect(jsonPath("$.data.updateExternalIdentity.userId").value(nextUser.getId().toString()))
            .andExpect(jsonPath("$.data.updateExternalIdentity.externalWorkspaceId").value("T999"))
            .andExpect(jsonPath("$.data.updateExternalIdentity.externalUserId").value("U999"))
            .andExpect(jsonPath("$.data.updateExternalIdentity.email").value("updated@example.com"))
            .andExpect(jsonPath("$.data.updateExternalIdentity.displayName").value("Updated Slack Person"))
            .andExpect(jsonPath("$.data.updateExternalIdentity.principalKey").value("SLACK_USER:T999:U999"));

        graphQl(adminSession, """
            query {
              externalIdentities {
                items {
                  id
                  provider
                  externalWorkspaceId
                  externalUserId
                }
              }
            }
            """)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.externalIdentities.items[*].id").value(hasItem(identityId)));

        graphQl(adminSession, """
            mutation {
              deleteExternalIdentity(id: "%s")
            }
            """.formatted(identityId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.deleteExternalIdentity").value(true));

        graphQl(adminSession, """
            query {
              externalIdentities {
                items {
                  id
                }
              }
            }
            """)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.externalIdentities.items[*].id").value(not(hasItem(identityId))));
    }

    @Test
    void rejectsIdentityMappingForDeletedWorkspaceOrUser() throws Exception {
        String suffix = UUID.randomUUID().toString();
        UserEntity owner = users.save(UserEntity.create("identity-owner-" + suffix + "@example.com", "Owner"));
        UserEntity deletedUser = users.save(UserEntity.create("deleted-identity-user-" + suffix + "@example.com", "Deleted"));
        deletedUser.markDeleted();
        users.saveAndFlush(deletedUser);
        UUID deletedWorkspaceId = jdbcTemplate.queryForObject("""
            INSERT INTO workspaces (owner_user_id, name, deleted_at)
            VALUES (?, 'Deleted workspace', now())
            RETURNING id
            """, UUID.class, owner.getId());
        WorkspaceEntity activeWorkspace = workspaces.save(WorkspaceEntity.create(owner.getId(), "Active workspace"));
        MockHttpSession adminSession = loginAs("identity-reject-admin-" + suffix + "@example.com");

        graphQl(adminSession, """
            mutation {
              createExternalIdentity(input: {
                provider: SLACK,
                workspaceId: "%s",
                userId: "%s",
                externalWorkspaceId: "T123",
                externalUserId: "U123"
              }) {
                id
              }
            }
            """.formatted(deletedWorkspaceId, owner.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.errors[0].message").value("워크스페이스를 찾을 수 없습니다"));

        graphQl(adminSession, """
            mutation {
              createExternalIdentity(input: {
                provider: SLACK,
                workspaceId: "%s",
                userId: "%s",
                externalWorkspaceId: "T124",
                externalUserId: "U124"
              }) {
                id
              }
            }
            """.formatted(activeWorkspace.getId(), deletedUser.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.errors[0].message").value("유저를 찾을 수 없습니다"));
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
