package com.mydata.admin;

import com.jayway.jsonpath.JsonPath;
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
import com.mydata.workspaces.WorkspaceEntity;
import com.mydata.workspaces.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class AdminDataSourceControllerTest extends PostgresIntegrationTest {
    MockMvc mockMvc;
    @Autowired WebApplicationContext webApplicationContext;
    @Autowired UserRepository users;
    @Autowired WorkspaceRepository workspaces;
    @Autowired DataSourceRepository dataSources;
    @Autowired IngestionJobRepository ingestionJobs;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void requestsManualDataSourceSync() throws Exception {
        UserEntity user = users.save(UserEntity.create("owner@example.com", "Owner"));
        WorkspaceEntity workspace = workspaces.save(WorkspaceEntity.create(user.getId(), "Personal"));
        DataSourceEntity dataSource = dataSources.save(DataSourceEntity.create(
            workspace.getId(),
            DataSourceType.LOCAL_TEXT,
            "Local notes",
            DataSourceStatus.ACTIVE,
            SyncMode.MANUAL
        ));

        MvcResult result = mockMvc.perform(post("/admin/data-sources/{id}/sync", dataSource.getId())
                .header("X-Admin-Token", "test-admin-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "requestedByUserId": "%s" }
                    """.formatted(user.getId())))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.dataSourceId").value(dataSource.getId().toString()))
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.triggerType").value("MANUAL"))
            .andReturn();

        String jobId = JsonPath.read(result.getResponse().getContentAsString(), "$.jobId");
        IngestionJobEntity job = ingestionJobs.findById(UUID.fromString(jobId)).orElseThrow();
        assertThat(job.getWorkspaceId()).isEqualTo(workspace.getId());
        assertThat(job.getDataSourceId()).isEqualTo(dataSource.getId());
        assertThat(job.getRequestedByUserId()).isEqualTo(user.getId());
        assertThat(job.getStatus()).isEqualTo(IngestionJobStatus.PENDING);
        assertThat(job.getTriggerType()).isEqualTo(IngestionTriggerType.MANUAL);
    }

    @Test
    void rejectsMalformedBodyWithInvalidAdminTokenBeforeParsingBody() throws Exception {
        mockMvc.perform(post("/admin/data-sources/{id}/sync", UUID.randomUUID())
                .header("X-Admin-Token", "wrong-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsMalformedBodyWithMissingAdminTokenBeforeParsingBody() throws Exception {
        mockMvc.perform(post("/admin/data-sources/{id}/sync", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"))
            .andExpect(status().isUnauthorized());
    }
}
