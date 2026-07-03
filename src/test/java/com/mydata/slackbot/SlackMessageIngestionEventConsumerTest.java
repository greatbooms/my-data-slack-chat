package com.mydata.slackbot;

import com.mydata.auth.Permission;
import com.mydata.auth.PrincipalKeys;
import com.mydata.connectors.slack.SlackChannelConnector;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SlackMessageIngestionEventConsumerTest extends PostgresIntegrationTest {
    private static final TypeReference<Map<String, Object>> METADATA_TYPE = new TypeReference<>() {
    };

    @Autowired UserRepository users;
    @Autowired WorkspaceRepository workspaces;
    @Autowired DataSourceRepository dataSources;
    @Autowired ExternalDocumentRepository documents;
    @Autowired DocumentAclEntryRepository aclEntries;
    @Autowired DocumentChunkRepository chunks;
    @Autowired SlackMessageIngestionEventConsumer consumer;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void ingestsSlackChannelMessageEventIntoMatchingDataSource() throws Exception {
        UserEntity user = users.save(UserEntity.create("slack-event-owner@example.com", "Slack Owner"));
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

        consumer.accept(new SlackMessageIngestionEvent(
            "T123",
            "C123",
            "U111",
            "Event message text",
            "1710000005.000100",
            "1710000000.000000",
            "channel"
        ));

        ExternalDocumentEntity document = documents
            .findByDataSourceIdAndExternalId(dataSource.getId(), "C123:1710000005.000100")
            .orElseThrow();
        assertThat(document.getUri()).isEqualTo("https://workspace.slack.com/archives/C123/p1710000005000100");
        assertThat(metadata(document))
            .containsEntry("slackChannelId", "C123")
            .containsEntry("slackUserId", "U111")
            .containsEntry("slackThreadTs", "1710000000.000000")
            .containsEntry("slackIsThreadReply", true);
        assertThat(chunks.findByDocumentIdOrderByChunkIndex(document.getId()))
            .singleElement()
            .satisfies(chunk -> assertThat(chunk.getContent()).contains("Event message text"));
        assertThat(aclEntries.findByDocumentId(document.getId()))
            .singleElement()
            .satisfies(acl -> {
                assertThat(acl.getPrincipalKey()).isEqualTo(PrincipalKeys.workspace(workspace.getId()));
                assertThat(acl.getPermission()).isEqualTo(Permission.READ);
                assertThat(acl.getSource()).isEqualTo("SLACK");
            });
    }

    @Test
    void ignoresMessageEventWhenNoActiveSlackDataSourceMatchesChannel() {
        long documentCount = documents.count();

        consumer.accept(new SlackMessageIngestionEvent(
            "T123",
            "C999",
            "U111",
            "No matching source",
            "1710000005.000100",
            null,
            "channel"
        ));

        assertThat(documents.count()).isEqualTo(documentCount);
    }

    @Test
    void ignoresMessageEventWhenMatchingSlackDataSourceIsPaused() {
        UserEntity user = users.save(UserEntity.create("paused-slack-event-owner@example.com", "Slack Owner"));
        WorkspaceEntity workspace = workspaces.save(WorkspaceEntity.create(user.getId(), "Slack workspace"));
        DataSourceEntity dataSource = DataSourceEntity.create(
            workspace.getId(),
            DataSourceType.SLACK,
            "Paused Slack channel",
            DataSourceStatus.PAUSED,
            SyncMode.MANUAL
        );
        dataSource.assignOwner(user.getId());
        dataSource.putConfig(SlackChannelConnector.CHANNEL_ID_CONFIG_KEY, "C123");
        dataSource = dataSources.saveAndFlush(dataSource);

        consumer.accept(new SlackMessageIngestionEvent(
            "T123",
            "C123",
            "U111",
            "Paused source message",
            "1710000005.000100",
            null,
            "channel"
        ));

        assertThat(documents.findByDataSourceIdAndExternalId(dataSource.getId(), "C123:1710000005.000100"))
            .isEmpty();
    }

    private Map<String, Object> metadata(ExternalDocumentEntity document) throws Exception {
        return objectMapper.readValue(document.getMetadataJson(), METADATA_TYPE);
    }
}
