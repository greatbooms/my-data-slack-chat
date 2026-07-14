package com.mydata.ingestion;

import com.mydata.auth.Permission;
import com.mydata.auth.PrincipalKeys;
import com.mydata.connectors.slack.SlackChannelConnector;
import com.mydata.connectors.slack.SlackClient;
import com.mydata.datasources.DataSourceEntity;
import com.mydata.datasources.DataSourceRepository;
import com.mydata.datasources.DataSourceStatus;
import com.mydata.datasources.DataSourceType;
import com.mydata.datasources.DataSourceVisibility;
import com.mydata.datasources.SyncMode;
import com.mydata.documents.DocumentAclEntryRepository;
import com.mydata.documents.DocumentChunkRepository;
import com.mydata.documents.ExternalDocumentEntity;
import com.mydata.documents.ExternalDocumentRepository;
import com.mydata.support.PostgresIntegrationTest;
import com.mydata.users.UserEntity;
import com.mydata.users.UserRepository;
import com.mydata.workspaces.WorkspaceEntity;
import com.mydata.workspaces.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SlackIngestionIntegrationTest extends PostgresIntegrationTest {
    private static final TypeReference<Map<String, Object>> METADATA_TYPE = new TypeReference<>() {
    };

    @Autowired UserRepository users;
    @Autowired WorkspaceRepository workspaces;
    @Autowired DataSourceRepository dataSources;
    @Autowired IngestionJobRepository ingestionJobs;
    @Autowired IngestionJobItemRepository jobItems;
    @Autowired IngestionWorker worker;
    @Autowired ExternalDocumentRepository documents;
    @Autowired DocumentAclEntryRepository aclEntries;
    @Autowired DocumentChunkRepository chunks;
    @Autowired FakeSlackClient slack;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void resetSlack() {
        slack.reset();
    }

    @Test
    void workerIngestsSlackChannelMessagesIntoDocumentsChunksAndAcl() throws Exception {
        UserEntity user = users.save(UserEntity.create("slack-ingestion-owner@example.com", "Slack Owner"));
        WorkspaceEntity workspace = workspaces.save(WorkspaceEntity.create(user.getId(), "Slack workspace"));
        DataSourceEntity dataSource = DataSourceEntity.create(
            workspace.getId(),
            DataSourceType.SLACK,
            "Slack channel",
            DataSourceStatus.ACTIVE,
            SyncMode.MANUAL
        );
        dataSource.assignOwner(user.getId());
        dataSource.changeVisibility(DataSourceVisibility.WORKSPACE);
        dataSource.putConfig(SlackChannelConnector.CHANNEL_ID_CONFIG_KEY, "C123");
        dataSource.putConfig(SlackChannelConnector.WORKSPACE_URL_CONFIG_KEY, "https://workspace.slack.com");
        dataSource = dataSources.saveAndFlush(dataSource);
        slack.channelMessages = List.of(new SlackClient.SlackMessage(
            "C123",
            "1710000000.000100",
            "1710000000.000100",
            "U111",
            "Root message",
            Instant.parse("2026-07-01T00:00:00Z"),
            false,
            false,
            null
        ));
        IngestionJobEntity job = ingestionJobs.saveAndFlush(IngestionJobEntity.pending(
            workspace.getId(),
            dataSource.getId(),
            IngestionTriggerType.MANUAL,
            user.getId()
        ));

        worker.run(job.getId());

        ExternalDocumentEntity document = documents
            .findByDataSourceIdAndExternalId(dataSource.getId(), "C123:1710000000.000100")
            .orElseThrow();
        assertThat(document.getTitle()).isEqualTo("Slack C123 1710000000.000100");
        assertThat(document.getSourceType()).isEqualTo(DataSourceType.SLACK.name());
        assertThat(document.getUri()).isEqualTo("https://workspace.slack.com/archives/C123/p1710000000000100");
        assertThat(metadata(document))
            .containsEntry("slackChannelId", "C123")
            .containsEntry("slackUserId", "U111")
            .containsEntry("slackPermalink", "https://workspace.slack.com/archives/C123/p1710000000000100")
            .containsEntry("slackIsThreadReply", false);
        assertThat(chunks.findByDocumentIdOrderByChunkIndex(document.getId()))
            .hasSize(1)
            .first()
            .satisfies(chunk -> assertThat(chunk.getContent()).contains("Root message"));
        assertThat(aclEntries.findByDocumentId(document.getId()))
            .hasSize(1)
            .first()
            .satisfies(acl -> {
                assertThat(acl.getPrincipalKey()).isEqualTo(PrincipalKeys.workspace(workspace.getId()));
                assertThat(acl.getPermission()).isEqualTo(Permission.READ);
                assertThat(acl.getSource()).isEqualTo("SLACK");
                assertThat(acl.isInherited()).isFalse();
            });
        assertThat(ingestionJobs.findById(job.getId()).orElseThrow().getStatus())
            .isEqualTo(IngestionJobStatus.SUCCEEDED);
        assertThat(jobItems.findByJobIdOrderByProcessedAtAscIdAsc(job.getId()))
            .singleElement()
            .satisfies(item -> {
                assertThat(item.getExternalId()).isEqualTo("data-source:C123:1710000000.000100");
                assertThat(item.getDocumentId()).isEqualTo(document.getId());
                assertThat(item.getStatus()).isEqualTo(IngestionJobItemStatus.SUCCEEDED);
                assertThat(item.getReason()).isNull();
            });
    }

    @Test
    void workerPersistsAndReusesSlackSyncCursor() {
        UserEntity user = users.save(UserEntity.create("slack-cursor-owner@example.com", "Slack Owner"));
        WorkspaceEntity workspace = workspaces.save(WorkspaceEntity.create(user.getId(), "Slack workspace"));
        DataSourceEntity dataSource = DataSourceEntity.create(
            workspace.getId(),
            DataSourceType.SLACK,
            "Slack channel",
            DataSourceStatus.ACTIVE,
            SyncMode.MANUAL
        );
        dataSource.assignOwner(user.getId());
        dataSource.putConfig(SlackChannelConnector.CHANNEL_ID_CONFIG_KEY, "C123");
        dataSource = dataSources.saveAndFlush(dataSource);
        slack.channelMessages = List.of(new SlackClient.SlackMessage(
            "C123",
            "1710000000.000100",
            "1710000000.000100",
            "U111",
            "Root message",
            Instant.parse("2026-07-01T00:00:00Z"),
            false,
            false,
            null
        ));
        IngestionJobEntity firstJob = ingestionJobs.saveAndFlush(IngestionJobEntity.pending(
            workspace.getId(),
            dataSource.getId(),
            IngestionTriggerType.MANUAL,
            user.getId()
        ));

        worker.run(firstJob.getId());

        UUID firstMessageId = documents.findByDataSourceIdAndExternalId(
            dataSource.getId(), "C123:1710000000.000100"
        ).orElseThrow().getId();

        assertThat(slack.requestedOldestMessageTs).containsExactly((String) null);
        assertThat(dataSources.findById(dataSource.getId()).orElseThrow().syncCursorValue())
            .containsEntry("latestMessageTs", "1710000000.000100")
            .doesNotContainKey("trackedThreadRootTs");

        slack.channelMessages = List.of(new SlackClient.SlackMessage(
            "C123",
            "1710000005.000100",
            "1710000005.000100",
            "U111",
            "Next root message",
            Instant.parse("2026-07-01T00:00:05Z"),
            false,
            false,
            null
        ));
        IngestionJobEntity secondJob = ingestionJobs.saveAndFlush(IngestionJobEntity.pending(
            workspace.getId(),
            dataSource.getId(),
            IngestionTriggerType.MANUAL,
            user.getId()
        ));

        worker.run(secondJob.getId());

        assertThat(slack.requestedOldestMessageTs)
            .containsExactly(null, "1710000000.000100");
        assertThat(dataSources.findById(dataSource.getId()).orElseThrow().syncCursorValue())
            .containsEntry("latestMessageTs", "1710000005.000100")
            .doesNotContainKey("trackedThreadRootTs");
        assertThat(documents.findById(firstMessageId).orElseThrow().getDeletedAt()).isNull();
        assertThat(documents.findByDataSourceIdAndExternalId(
            dataSource.getId(), "C123:1710000005.000100"
        )).isPresent();
    }

    private Map<String, Object> metadata(ExternalDocumentEntity document) throws Exception {
        return objectMapper.readValue(document.getMetadataJson(), METADATA_TYPE);
    }

    @TestConfiguration
    static class FakeSlackConfiguration {
        @Bean
        @Primary
        FakeSlackClient fakeSlackClient() {
            return new FakeSlackClient();
        }
    }

    static class FakeSlackClient implements SlackClient {
        private List<SlackMessage> channelMessages = List.of();
        private final java.util.ArrayList<String> requestedOldestMessageTs = new java.util.ArrayList<>();

        void reset() {
            channelMessages = List.of();
            requestedOldestMessageTs.clear();
        }

        @Override
        public List<SlackMessage> listChannelMessages(String channelId, String oldestMessageTs) {
            requestedOldestMessageTs.add(oldestMessageTs);
            return channelMessages;
        }

        @Override
        public List<SlackMessage> listThreadReplies(String channelId, String threadTs) {
            return List.of();
        }
    }
}
