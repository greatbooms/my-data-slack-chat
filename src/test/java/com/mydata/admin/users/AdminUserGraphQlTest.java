package com.mydata.admin.users;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class AdminUserGraphQlTest extends PostgresIntegrationTest {
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
    void managesUsersThroughGraphQl() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String adminEmail = "admin-" + suffix + "@example.com";
        String userEmail = "user-" + suffix + "@example.com";
        MockHttpSession adminSession = loginAs(adminEmail);
        int initialActiveUsers = users.findByDeletedAtIsNullOrderByCreatedAtDesc().size();

        MvcResult createResult = graphQl(adminSession, """
            mutation {
              createUser(input: {
                email: "%s",
                displayName: "사용자",
                role: USER,
                password: "secret1234"
              }) {
                id
                email
                role
                status
                deletedAt
              }
            }
            """.formatted(userEmail))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.createUser.email").value(userEmail))
            .andExpect(jsonPath("$.data.createUser.role").value("USER"))
            .andExpect(jsonPath("$.data.createUser.status").value("ACTIVE"))
            .andExpect(jsonPath("$.data.createUser.deletedAt").doesNotExist())
            .andReturn();

        String userId = JsonPaths.readString(createResult, "$.data.createUser.id");
        Integer personalWorkspaceCount = jdbcTemplate.queryForObject("""
            SELECT count(*)
            FROM workspaces
            WHERE owner_user_id = ?::uuid
              AND name = 'Personal'
              AND deleted_at IS NULL
            """, Integer.class, userId);
        assertThat(personalWorkspaceCount).isEqualTo(1);

        graphQl(adminSession, """
            query {
              users {
                totalCount
                items {
                  email
                }
              }
            }
            """)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.users.totalCount").value(initialActiveUsers + 1))
            .andExpect(jsonPath("$.data.users.items[*].email").value(hasItem(userEmail)));

        graphQl(adminSession, """
            mutation {
              disableUser(id: "%s") {
                status
              }
            }
            """.formatted(userId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.disableUser.status").value("DISABLED"));

        graphQl(adminSession, """
            mutation {
              softDeleteUser(id: "%s") {
                deletedAt
              }
            }
            """.formatted(userId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.softDeleteUser.deletedAt").isNotEmpty());

        graphQl(adminSession, """
            query {
              users {
                items {
                  email
                }
              }
            }
            """)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.users.items[*].email").value(not(hasItem(userEmail))));

        graphQl(adminSession, """
            mutation {
              restoreUser(id: "%s") {
                status
                deletedAt
              }
            }
            """.formatted(userId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.restoreUser.status").value("ACTIVE"))
            .andExpect(jsonPath("$.data.restoreUser.deletedAt").doesNotExist());

        graphQl(adminSession, """
            mutation {
              resetUserPassword(id: "%s", input: { password: "changed1234" }) {
                id
              }
            }
            """.formatted(userId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.resetUserPassword.id").value(userId));

        UserEntity user = users.findById(userIdAsUuid(userId)).orElseThrow();
        assertThat(passwordEncoder.matches("changed1234", user.getPasswordHash())).isTrue();
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

    private static UUID userIdAsUuid(String userId) {
        return UUID.fromString(userId);
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
