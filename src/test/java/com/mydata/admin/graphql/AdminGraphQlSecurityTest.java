package com.mydata.admin.graphql;

import com.mydata.datasources.DataSourceEntity;
import com.mydata.datasources.DataSourceRepository;
import com.mydata.datasources.DataSourceStatus;
import com.mydata.datasources.DataSourceType;
import com.mydata.datasources.SyncMode;
import com.mydata.ingestion.IngestionJobEntity;
import com.mydata.ingestion.IngestionJobRepository;
import com.mydata.ingestion.IngestionJobStatus;
import com.mydata.ingestion.IngestionTriggerType;
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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class AdminGraphQlSecurityTest extends PostgresIntegrationTest {
    private static final String SECRET_HASH =
        "$2a$10$kxV0KctAZ0SDCwR6fmM6GO4e6uMh4kWxMWu4c7vEzV1Khaj6HNQge";

    MockMvc mockMvc;
    @Autowired WebApplicationContext webApplicationContext;
    @Autowired UserRepository users;
    @Autowired WorkspaceRepository workspaces;
    @Autowired DataSourceRepository dataSources;
    @Autowired IngestionJobRepository ingestionJobs;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .apply(springSecurity())
            .build();
    }

    @Test
    void returnsViewerAndDashboardSummaryForLoggedInAdmin() throws Exception {
        int initialUserCount = activeUserCount();
        int initialDataSourceCount = activeDataSourceCount();
        int initialRunningJobCount = runningJobCount();

        UserEntity admin = users.save(admin("admin@example.com", "관리자"));
        users.save(UserEntity.create("user@example.com", "사용자"));
        UserEntity deletedUser = UserEntity.create("deleted@example.com", "삭제 사용자");
        deletedUser.markDeleted();
        users.save(deletedUser);

        WorkspaceEntity workspace = workspaces.save(WorkspaceEntity.create(admin.getId(), "Personal"));
        DataSourceEntity activeDataSource = dataSources.save(DataSourceEntity.create(
            workspace.getId(),
            DataSourceType.LOCAL_TEXT,
            "Local notes",
            DataSourceStatus.ACTIVE,
            SyncMode.MANUAL
        ));
        DataSourceEntity deletedDataSource = DataSourceEntity.create(
            workspace.getId(),
            DataSourceType.LOCAL_TEXT,
            "Deleted notes",
            DataSourceStatus.ACTIVE,
            SyncMode.MANUAL
        );
        deletedDataSource.markDeleted();
        dataSources.save(deletedDataSource);
        IngestionJobEntity runningJob = IngestionJobEntity.pending(
            workspace.getId(),
            activeDataSource.getId(),
            IngestionTriggerType.MANUAL,
            admin.getId()
        );
        runningJob.markRunning();
        ingestionJobs.save(runningJob);
        ingestionJobs.save(IngestionJobEntity.pending(
            workspace.getId(),
            activeDataSource.getId(),
            IngestionTriggerType.MANUAL,
            admin.getId()
        ));

        MockHttpSession adminSession = loginAs("admin@example.com");

        mockMvc.perform(post("/admin/graphql")
                .session(adminSession)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "query": "query { viewer { email displayName role } dashboardSummary { userCount dataSourceCount runningJobCount } }"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.viewer.email").value("admin@example.com"))
            .andExpect(jsonPath("$.data.viewer.displayName").value("관리자"))
            .andExpect(jsonPath("$.data.viewer.role").value("ADMIN"))
            .andExpect(jsonPath("$.data.dashboardSummary.userCount").value(initialUserCount + 2))
            .andExpect(jsonPath("$.data.dashboardSummary.dataSourceCount").value(initialDataSourceCount + 1))
            .andExpect(jsonPath("$.data.dashboardSummary.runningJobCount").value(initialRunningJobCount + 1));
    }

    private MockHttpSession loginAs(String email) throws Exception {
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

    private static UserEntity admin(String email, String displayName) {
        UserEntity user = UserEntity.create(email, displayName);
        user.changeRole(UserRole.ADMIN);
        user.changeStatus(UserStatus.ACTIVE);
        user.updatePasswordHash(SECRET_HASH);
        return user;
    }

    private int activeUserCount() {
        return users.findByDeletedAtIsNullOrderByCreatedAtDesc().size();
    }

    private int activeDataSourceCount() {
        return dataSources.findByDeletedAtIsNullOrderByCreatedAtDesc().size();
    }

    private int runningJobCount() {
        return (int) ingestionJobs.findAll().stream()
            .filter(job -> job.getStatus() == IngestionJobStatus.RUNNING)
            .count();
    }
}
