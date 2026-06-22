package com.mydata.admin.auth;

import com.mydata.support.PostgresIntegrationTest;
import com.mydata.users.UserEntity;
import com.mydata.users.UserRepository;
import com.mydata.users.UserRole;
import com.mydata.users.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class AdminAuthenticationTest extends PostgresIntegrationTest {
    private static final String SECRET_HASH =
        "$2a$10$kxV0KctAZ0SDCwR6fmM6GO4e6uMh4kWxMWu4c7vEzV1Khaj6HNQge";

    MockMvc mockMvc;
    @Autowired WebApplicationContext webApplicationContext;
    @Autowired UserRepository users;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .apply(springSecurity())
            .build();
    }

    @Test
    void logsInActiveAdminWithEmailAndPassword() throws Exception {
        createUser("admin@example.com", UserRole.ADMIN, UserStatus.ACTIVE, false);

        mockMvc.perform(post("/admin/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@example.com\",\"password\":\"secret1234\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void rejectsLoginWithoutCsrfToken() throws Exception {
        createUser("admin-without-csrf@example.com", UserRole.ADMIN, UserStatus.ACTIVE, false);

        mockMvc.perform(post("/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin-without-csrf@example.com\",\"password\":\"secret1234\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void rejectsAnonymousAdminGraphqlRequest() throws Exception {
        mockMvc.perform(post("/admin/graphql")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"{ viewer { email } }\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsAuthenticatedAdminGraphqlRequestWithoutCsrfToken() throws Exception {
        mockMvc.perform(post("/admin/graphql")
                .with(user("admin@example.com").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"{ viewer { email } }\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void rejectsDisabledAdminLogin() throws Exception {
        createUser("disabled-admin@example.com", UserRole.ADMIN, UserStatus.DISABLED, false);

        mockMvc.perform(post("/admin/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"disabled-admin@example.com\",\"password\":\"secret1234\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsDeletedAdminLogin() throws Exception {
        createUser("deleted-admin@example.com", UserRole.ADMIN, UserStatus.ACTIVE, true);

        mockMvc.perform(post("/admin/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"deleted-admin@example.com\",\"password\":\"secret1234\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsNonAdminUserLogin() throws Exception {
        createUser("user@example.com", UserRole.USER, UserStatus.ACTIVE, false);

        mockMvc.perform(post("/admin/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"user@example.com\",\"password\":\"secret1234\"}"))
            .andExpect(status().isUnauthorized());
    }

    private void createUser(String email, UserRole role, UserStatus status, boolean deleted) {
        UserEntity user = UserEntity.create(email, email);
        user.changeRole(role);
        user.changeStatus(status);
        user.updatePasswordHash(SECRET_HASH);
        if (deleted) {
            user.markDeleted();
        }
        users.save(user);
    }
}
