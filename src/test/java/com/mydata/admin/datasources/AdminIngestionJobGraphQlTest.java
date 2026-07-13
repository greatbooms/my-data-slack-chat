package com.mydata.admin.datasources;

import com.mydata.datasources.DataSourceEntity;
import com.mydata.datasources.DataSourceRepository;
import com.mydata.datasources.DataSourceStatus;
import com.mydata.datasources.DataSourceType;
import com.mydata.datasources.SyncMode;
import com.mydata.documents.ExternalDocumentEntity;
import com.mydata.documents.ExternalDocumentRepository;
import com.mydata.ingestion.IngestionJobEntity;
import com.mydata.ingestion.IngestionJobItemEntity;
import com.mydata.ingestion.IngestionJobItemRepository;
import com.mydata.ingestion.IngestionJobRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class AdminIngestionJobGraphQlTest extends PostgresIntegrationTest {
    MockMvc mockMvc;
    @Autowired WebApplicationContext webApplicationContext;
    @Autowired UserRepository users;
    @Autowired WorkspaceRepository workspaces;
    @Autowired DataSourceRepository dataSources;
    @Autowired IngestionJobRepository ingestionJobs;
    @Autowired IngestionJobItemRepository jobItems;
    @Autowired ExternalDocumentRepository documents;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .apply(springSecurity())
            .build();
    }

    @Test
    void listsPartialFailedJobWithSucceededAndFailedItemCounts() throws Exception {
        Fixture fixture = fixture("partial-counts");
        fixture.job().markRunning();
        ingestionJobs.saveAndFlush(fixture.job());
        ExternalDocumentEntity firstDocument = document(fixture, "success-1");
        ExternalDocumentEntity secondDocument = document(fixture, "success-2");
        jobItems.saveAllAndFlush(List.of(
            IngestionJobItemEntity.succeeded(fixture.job().getId(), "success-1", firstDocument.getId()),
            IngestionJobItemEntity.succeeded(fixture.job().getId(), "success-2", secondDocument.getId()),
            IngestionJobItemEntity.failed(fixture.job().getId(), "failure-1", "읽기 실패")
        ));
        fixture.job().markPartialFailed(3, 1);
        ingestionJobs.saveAndFlush(fixture.job());
        MockHttpSession adminSession = loginAs("partial-counts-admin-" + UUID.randomUUID() + "@example.com");

        graphQl(adminSession, """
            query {
              ingestionJobs(dataSourceId: "%s") {
                status
                succeededItemCount
                failedItemCount
              }
            }
            """.formatted(fixture.dataSource().getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.ingestionJobs.length()").value(1))
            .andExpect(jsonPath("$.data.ingestionJobs[0].status").value("PARTIAL_FAILED"))
            .andExpect(jsonPath("$.data.ingestionJobs[0].succeededItemCount").value(2))
            .andExpect(jsonPath("$.data.ingestionJobs[0].failedItemCount").value(1));
    }

    @Test
    void paginatesFailedItemsForSelectedIngestionJob() throws Exception {
        Fixture fixture = fixture("failure-pagination");
        OffsetDateTime processedAt = OffsetDateTime.parse("2026-07-13T01:02:03Z");
        long idPrefix = fixture.job().getId().getMostSignificantBits();
        insertFailedItem(
            new UUID(idPrefix, 1L),
            fixture.job().getId(),
            "failure-first",
            "첫 번째 실패",
            processedAt
        );
        insertFailedItem(
            new UUID(idPrefix, 2L),
            fixture.job().getId(),
            "failure-second",
            "두 번째 실패",
            processedAt
        );
        MockHttpSession adminSession = loginAs("pagination-admin-" + UUID.randomUUID() + "@example.com");

        MvcResult firstPage = graphQl(adminSession, """
            query {
              ingestionJobItems(jobId: "%s", status: FAILED, first: 1) {
                items { externalId documentId status reason processedAt }
                hasNextPage
                endCursor
              }
            }
            """.formatted(fixture.job().getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.ingestionJobItems.items.length()").value(1))
            .andExpect(jsonPath("$.data.ingestionJobItems.items[0].externalId").value("failure-first"))
            .andExpect(jsonPath("$.data.ingestionJobItems.items[0].status").value("FAILED"))
            .andExpect(jsonPath("$.data.ingestionJobItems.hasNextPage").value(true))
            .andExpect(jsonPath("$.data.ingestionJobItems.endCursor").isNotEmpty())
            .andReturn();

        String endCursor = JsonPaths.readString(firstPage, "$.data.ingestionJobItems.endCursor");
        graphQl(adminSession, """
            query {
              ingestionJobItems(jobId: "%s", status: FAILED, first: 1, after: "%s") {
                items { externalId status }
                hasNextPage
                endCursor
              }
            }
            """.formatted(fixture.job().getId(), endCursor))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.ingestionJobItems.items.length()").value(1))
            .andExpect(jsonPath("$.data.ingestionJobItems.items[0].externalId").value("failure-second"))
            .andExpect(jsonPath("$.data.ingestionJobItems.hasNextPage").value(false))
            .andExpect(jsonPath("$.data.ingestionJobItems.endCursor").doesNotExist());
    }

    @Test
    void filtersItemsByStatusAndDoesNotLeakAnotherJobsItems() throws Exception {
        Fixture fixture = fixture("status-filter");
        ExternalDocumentEntity document = document(fixture, "target-success");
        jobItems.saveAllAndFlush(List.of(
            IngestionJobItemEntity.succeeded(fixture.job().getId(), "target-success", document.getId()),
            IngestionJobItemEntity.failed(fixture.job().getId(), "target-failure", "대상 실패")
        ));
        IngestionJobEntity anotherJob = ingestionJobs.saveAndFlush(IngestionJobEntity.pending(
            fixture.workspace().getId(),
            fixture.dataSource().getId(),
            IngestionTriggerType.MANUAL,
            fixture.owner().getId()
        ));
        jobItems.saveAndFlush(IngestionJobItemEntity.failed(
            anotherJob.getId(),
            "another-job-failure",
            "다른 job 실패"
        ));
        MockHttpSession adminSession = loginAs("status-filter-admin-" + UUID.randomUUID() + "@example.com");

        graphQl(adminSession, """
            query {
              ingestionJobItems(jobId: "%s", status: FAILED) {
                items { externalId status }
                hasNextPage
              }
            }
            """.formatted(fixture.job().getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.ingestionJobItems.items.length()").value(1))
            .andExpect(jsonPath("$.data.ingestionJobItems.items[0].externalId").value("target-failure"))
            .andExpect(jsonPath("$.data.ingestionJobItems.items[0].status").value("FAILED"))
            .andExpect(jsonPath("$.data.ingestionJobItems.hasNextPage").value(false));
    }

    @Test
    void rejectsInvalidIngestionJobItemCursorAndPageSize() throws Exception {
        Fixture fixture = fixture("invalid-page");
        MockHttpSession adminSession = loginAs("invalid-page-admin-" + UUID.randomUUID() + "@example.com");

        graphQl(adminSession, """
            query {
              ingestionJobItems(jobId: "%s", after: "not-base64") {
                items { externalId }
              }
            }
            """.formatted(fixture.job().getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.errors[0].message").value("after cursor 형식이 올바르지 않습니다"));

        graphQl(adminSession, """
            query {
              ingestionJobItems(jobId: "%s", first: 101) {
                items { externalId }
              }
            }
            """.formatted(fixture.job().getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.errors[0].message").value("first는 1 이상 100 이하여야 합니다"));

        graphQl(adminSession, """
            query {
              ingestionJobItems(jobId: "%s", first: 0) {
                items { externalId }
              }
            }
            """.formatted(fixture.job().getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.errors[0].message").value("first는 1 이상 100 이하여야 합니다"));
    }

    @Test
    void rejectsItemsForJobOutsideActiveDataSourceWorkspace() throws Exception {
        Fixture fixture = fixture("deleted-data-source");
        fixture.dataSource().markDeleted();
        dataSources.saveAndFlush(fixture.dataSource());
        MockHttpSession adminSession = loginAs("deleted-source-admin-" + UUID.randomUUID() + "@example.com");

        assertJobItemsNotFound(adminSession, fixture.job().getId());
    }

    @Test
    void rejectsItemsForJobInDeletedWorkspace() throws Exception {
        Fixture fixture = fixture("deleted-workspace");
        fixture.workspace().markDeleted();
        workspaces.saveAndFlush(fixture.workspace());
        MockHttpSession adminSession = loginAs("deleted-workspace-admin-" + UUID.randomUUID() + "@example.com");

        assertJobItemsNotFound(adminSession, fixture.job().getId());
    }

    @Test
    void rejectsItemsWhenJobWorkspaceDoesNotMatchDataSource() throws Exception {
        Fixture fixture = fixture("mismatched-workspace");
        WorkspaceEntity anotherWorkspace = workspaces.save(WorkspaceEntity.create(
            fixture.owner().getId(),
            "Another workspace"
        ));
        IngestionJobEntity mismatchedJob = ingestionJobs.saveAndFlush(IngestionJobEntity.pending(
            anotherWorkspace.getId(),
            fixture.dataSource().getId(),
            IngestionTriggerType.MANUAL,
            fixture.owner().getId()
        ));
        MockHttpSession adminSession = loginAs("mismatched-workspace-admin-" + UUID.randomUUID() + "@example.com");

        assertJobItemsNotFound(adminSession, mismatchedJob.getId());
    }

    private Fixture fixture(String prefix) {
        String suffix = UUID.randomUUID().toString();
        UserEntity owner = users.save(UserEntity.create(prefix + "-owner-" + suffix + "@example.com", "Owner"));
        WorkspaceEntity workspace = workspaces.save(WorkspaceEntity.create(owner.getId(), prefix + " workspace"));
        DataSourceEntity dataSource = DataSourceEntity.create(
            workspace.getId(),
            DataSourceType.LOCAL_TEXT,
            prefix + " data source",
            DataSourceStatus.ACTIVE,
            SyncMode.MANUAL
        );
        dataSource.assignOwner(owner.getId());
        dataSource = dataSources.saveAndFlush(dataSource);
        IngestionJobEntity job = ingestionJobs.saveAndFlush(IngestionJobEntity.pending(
            workspace.getId(),
            dataSource.getId(),
            IngestionTriggerType.MANUAL,
            owner.getId()
        ));
        return new Fixture(owner, workspace, dataSource, job);
    }

    private ExternalDocumentEntity document(Fixture fixture, String externalId) {
        return documents.saveAndFlush(ExternalDocumentEntity.create(
            fixture.workspace().getId(),
            fixture.dataSource().getId(),
            externalId,
            DataSourceType.LOCAL_TEXT.name(),
            externalId,
            "hash-" + externalId
        ));
    }

    private void insertFailedItem(
        UUID id,
        UUID jobId,
        String externalId,
        String reason,
        OffsetDateTime processedAt
    ) {
        jdbcTemplate.update("""
            INSERT INTO ingestion_job_items (id, job_id, external_id, status, reason, processed_at)
            VALUES (?, ?, ?, 'FAILED', ?, ?)
            """, id, jobId, externalId, reason, processedAt);
    }

    private void assertJobItemsNotFound(MockHttpSession adminSession, UUID jobId) throws Exception {
        graphQl(adminSession, """
            query {
              ingestionJobItems(jobId: "%s") {
                items { externalId }
              }
            }
            """.formatted(jobId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.errors[0].message").value("수집 job을 찾을 수 없습니다"));
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

    private record Fixture(
        UserEntity owner,
        WorkspaceEntity workspace,
        DataSourceEntity dataSource,
        IngestionJobEntity job
    ) {
    }

    private static final class JsonPaths {
        private JsonPaths() {
        }

        static String readString(MvcResult result, String path) throws Exception {
            return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), path);
        }
    }
}
