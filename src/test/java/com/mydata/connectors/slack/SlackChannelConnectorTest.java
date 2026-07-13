package com.mydata.connectors.slack;

import com.mydata.auth.PrincipalKeys;
import com.mydata.connectors.core.ConnectorDocumentEvent;
import com.mydata.connectors.core.ConnectorEventSink;
import com.mydata.connectors.core.ConnectorFailureEvent;
import com.mydata.connectors.core.ConnectorItemReference;
import com.mydata.connectors.core.ConnectorItemType;
import com.mydata.connectors.core.DataSourceSnapshot;
import com.mydata.connectors.core.RawExternalDocument;
import com.mydata.connectors.core.SyncCursor;
import com.mydata.datasources.DataSourceEntity;
import com.mydata.datasources.DataSourceStatus;
import com.mydata.datasources.DataSourceType;
import com.mydata.datasources.DataSourceVisibility;
import com.mydata.datasources.SyncMode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SlackChannelConnectorTest {
    @Test
    void fetchChangesEmitsChannelMessagesAndThreadReplies() {
        UUID workspaceId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        DataSourceEntity dataSource = dataSource(workspaceId, ownerId, DataSourceVisibility.PRIVATE);
        dataSource.putConfig(SlackChannelConnector.CHANNEL_ID_CONFIG_KEY, "C123");
        dataSource.putConfig(SlackChannelConnector.WORKSPACE_URL_CONFIG_KEY, "https://workspace.slack.com/");
        FakeSlackClient slack = new FakeSlackClient();
        SlackClient.SlackMessage root = new SlackClient.SlackMessage(
            "C123",
            "1710000000.000100",
            "1710000000.000100",
            "U111",
            "Root message",
            Instant.parse("2026-07-01T00:00:00Z"),
            false,
            true,
            null
        );
        SlackClient.SlackMessage reply = new SlackClient.SlackMessage(
            "C123",
            "1710000001.000200",
            "1710000000.000100",
            "U222",
            "Thread reply",
            Instant.parse("2026-07-01T00:00:01Z"),
            true,
            false,
            null
        );
        slack.channelMessages = List.of(root);
        slack.threadReplies = List.of(root, reply);
        SlackChannelConnector connector = new SlackChannelConnector(slack);
        List<ConnectorDocumentEvent> events = new ArrayList<>();

        SyncCursor returnedCursor = connector.fetchChanges(
            snapshot(dataSource, new SyncCursor(Map.of())),
            recordingSink(events)
        );
        List<RawExternalDocument> documents = events.stream().map(ConnectorDocumentEvent::document).toList();

        assertThat(returnedCursor.value()).containsEntry("latestMessageTs", "1710000000.000100");
        assertThat(connector.supports()).isEqualTo(DataSourceType.SLACK);
        assertThat(slack.requestedChannelIds).containsExactly("C123");
        assertThat(slack.requestedOldestMessageTs).containsExactly((String) null);
        assertThat(slack.requestedThreadTs).containsExactly("1710000000.000100");
        assertThat(documents)
            .extracting(RawExternalDocument::externalId)
            .containsExactly("C123:1710000000.000100", "C123:1710000001.000200");
        assertThat(events)
            .extracting(ConnectorDocumentEvent::reference)
            .extracting(ConnectorItemReference::type)
            .containsOnly(ConnectorItemType.DATA_SOURCE);
        assertThat(events)
            .extracting(event -> event.reference().qualifiedExternalId())
            .containsExactly(
                "data-source:C123:1710000000.000100",
                "data-source:C123:1710000001.000200"
            );

        RawExternalDocument rootDocument = documents.get(0);
        assertThat(rootDocument.sourceType()).isEqualTo(DataSourceType.SLACK);
        assertThat(rootDocument.title()).isEqualTo("Slack C123 1710000000.000100");
        assertThat(rootDocument.uri()).isEqualTo("https://workspace.slack.com/archives/C123/p1710000000000100");
        assertThat(rootDocument.mimeType()).isEqualTo("text/plain");
        assertThat(rootDocument.externalCreatedAt()).isEqualTo(Instant.parse("2026-07-01T00:00:00Z"));
        assertThat(rootDocument.externalUpdatedAt()).isEqualTo(Instant.parse("2026-07-01T00:00:00Z"));
        assertThat(rootDocument.content().text()).isEqualTo("""
            Slack channel: C123
            User: U111
            Timestamp: 1710000000.000100
            Thread: 1710000000.000100
            Message:
            Root message
            """.stripTrailing());
        assertThat(rootDocument.contentHash()).isNotBlank();
        assertThat(rootDocument.metadata())
            .containsEntry("slackChannelId", "C123")
            .containsEntry("slackUserId", "U111")
            .containsEntry("slackMessageTs", "1710000000.000100")
            .containsEntry("slackThreadTs", "1710000000.000100")
            .containsEntry("slackIsThreadReply", false)
            .containsEntry("slackPermalink", "https://workspace.slack.com/archives/C123/p1710000000000100");
        assertThat(rootDocument.aclEntries()).singleElement().satisfies(acl -> {
            assertThat(acl.principalKey()).isEqualTo(PrincipalKeys.user(ownerId));
            assertThat(acl.permission()).isEqualTo("READ");
            assertThat(acl.inherited()).isFalse();
            assertThat(acl.source()).isEqualTo("SLACK");
        });

        RawExternalDocument replyDocument = documents.get(1);
        assertThat(replyDocument.content().text()).contains("Thread reply");
        assertThat(replyDocument.metadata())
            .containsEntry("slackUserId", "U222")
            .containsEntry("slackIsThreadReply", true);
    }

    @Test
    void fetchChangesPassesStoredCursorAndReturnsNewestSeenSlackTimestamp() {
        UUID workspaceId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        DataSourceEntity dataSource = dataSource(workspaceId, ownerId, DataSourceVisibility.PRIVATE);
        dataSource.putConfig(SlackChannelConnector.CHANNEL_ID_CONFIG_KEY, "C123");
        FakeSlackClient slack = new FakeSlackClient();
        SlackClient.SlackMessage root = new SlackClient.SlackMessage(
            "C123",
            "1710000010.000100",
            "1710000010.000100",
            "U111",
            "New root",
            Instant.parse("2026-07-01T00:00:10Z"),
            false,
            true,
            null
        );
        SlackClient.SlackMessage reply = new SlackClient.SlackMessage(
            "C123",
            "1710000015.000200",
            "1710000010.000100",
            "U222",
            "Newest reply",
            Instant.parse("2026-07-01T00:00:15Z"),
            true,
            false,
            null
        );
        slack.channelMessages = List.of(root);
        slack.threadReplies = List.of(root, reply);
        SlackChannelConnector connector = new SlackChannelConnector(slack);
        List<ConnectorDocumentEvent> events = new ArrayList<>();

        SyncCursor returnedCursor = connector.fetchChanges(
            snapshot(dataSource, new SyncCursor(Map.of("latestMessageTs", "1710000000.000100"))),
            recordingSink(events)
        );
        List<RawExternalDocument> documents = events.stream().map(ConnectorDocumentEvent::document).toList();

        assertThat(slack.requestedOldestMessageTs).containsExactly("1710000000.000100");
        assertThat(documents)
            .extracting(RawExternalDocument::externalId)
            .containsExactly("C123:1710000010.000100", "C123:1710000015.000200");
        assertThat(returnedCursor.value()).containsEntry("latestMessageTs", "1710000010.000100");
    }

    @Test
    void workspaceVisibilityUsesWorkspacePrincipal() {
        UUID workspaceId = UUID.randomUUID();
        DataSourceEntity dataSource = dataSource(workspaceId, UUID.randomUUID(), DataSourceVisibility.WORKSPACE);
        dataSource.putConfig(SlackChannelConnector.CHANNEL_ID_CONFIG_KEY, "C123");
        FakeSlackClient slack = new FakeSlackClient();
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
        SlackChannelConnector connector = new SlackChannelConnector(slack);
        List<RawExternalDocument> documents = new ArrayList<>();

        connector.fetchChanges(
            snapshot(dataSource, new SyncCursor(Map.of())),
            documentSink(documents)
        );

        assertThat(documents).singleElement()
            .satisfies(document -> assertThat(document.aclEntries()).singleElement()
                .satisfies(acl -> assertThat(acl.principalKey()).isEqualTo(PrincipalKeys.workspace(workspaceId))));
    }

    @Test
    void fetchChangesRequiresChannelId() {
        SlackChannelConnector connector = new SlackChannelConnector(new FakeSlackClient());
        DataSourceEntity dataSource = dataSource(UUID.randomUUID(), UUID.randomUUID(), DataSourceVisibility.PRIVATE);

        assertThatThrownBy(() -> connector.fetchChanges(
            snapshot(dataSource, new SyncCursor(Map.of())),
            documentSink(new ArrayList<>())
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(SlackChannelConnector.CHANNEL_ID_CONFIG_KEY);
    }

    private static DataSourceEntity dataSource(UUID workspaceId, UUID ownerId, DataSourceVisibility visibility) {
        DataSourceEntity dataSource = DataSourceEntity.create(
            workspaceId,
            DataSourceType.SLACK,
            "Slack",
            DataSourceStatus.ACTIVE,
            SyncMode.MANUAL
        );
        dataSource.assignOwner(ownerId);
        dataSource.changeVisibility(visibility);
        return dataSource;
    }

    private static DataSourceSnapshot snapshot(DataSourceEntity dataSource, SyncCursor cursor) {
        return new DataSourceSnapshot(
            dataSource.getId(),
            dataSource.getWorkspaceId(),
            dataSource.getOwnerUserId(),
            dataSource.getType(),
            dataSource.getVisibility(),
            dataSource.configValues(),
            cursor
        );
    }

    private static ConnectorEventSink documentSink(List<RawExternalDocument> documents) {
        return new ConnectorEventSink() {
            @Override
            public void onDocument(ConnectorDocumentEvent event) {
                documents.add(event.document());
            }

            @Override
            public void onFailure(ConnectorFailureEvent event) {
                throw new AssertionError("예상하지 않은 connector 실패 event: " + event);
            }
        };
    }

    private static ConnectorEventSink recordingSink(List<ConnectorDocumentEvent> events) {
        return new ConnectorEventSink() {
            @Override
            public void onDocument(ConnectorDocumentEvent event) {
                events.add(event);
            }

            @Override
            public void onFailure(ConnectorFailureEvent event) {
                throw new AssertionError("예상하지 않은 connector 실패 event: " + event);
            }
        };
    }

    private static class FakeSlackClient implements SlackClient {
        private List<SlackMessage> channelMessages = List.of();
        private List<SlackMessage> threadReplies = List.of();
        private final List<String> requestedChannelIds = new ArrayList<>();
        private final List<String> requestedOldestMessageTs = new ArrayList<>();
        private final List<String> requestedThreadTs = new ArrayList<>();

        @Override
        public List<SlackMessage> listChannelMessages(String channelId, String oldestMessageTs) {
            requestedChannelIds.add(channelId);
            requestedOldestMessageTs.add(oldestMessageTs);
            return channelMessages;
        }

        @Override
        public List<SlackMessage> listThreadReplies(String channelId, String threadTs) {
            requestedThreadTs.add(threadTs);
            return threadReplies;
        }
    }
}
