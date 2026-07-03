package com.mydata.connectors.slack;

import com.mydata.connectors.core.DataSourceConnector;
import com.mydata.connectors.core.DocumentHandler;
import com.mydata.connectors.core.SyncCursor;
import com.mydata.datasources.DataSourceEntity;
import com.mydata.datasources.DataSourceType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Component
public class SlackChannelConnector implements DataSourceConnector {
    public static final String CHANNEL_ID_CONFIG_KEY = "slackChannelId";
    public static final String WORKSPACE_URL_CONFIG_KEY = "slackWorkspaceUrl";
    public static final String LATEST_MESSAGE_TS_CURSOR_KEY = "latestMessageTs";

    private final SlackClient slackClient;
    private final SlackRawDocumentFactory rawDocuments;

    public SlackChannelConnector(SlackClient slackClient) {
        this(slackClient, new SlackRawDocumentFactory());
    }

    @Autowired
    public SlackChannelConnector(SlackClient slackClient, SlackRawDocumentFactory rawDocuments) {
        this.slackClient = slackClient;
        this.rawDocuments = rawDocuments;
    }

    @Override
    public DataSourceType supports() {
        return DataSourceType.SLACK;
    }

    @Override
    public SyncCursor fetchChanges(DataSourceEntity dataSource, SyncCursor cursor, DocumentHandler handler) {
        String channelId = requiredConfig(dataSource, CHANNEL_ID_CONFIG_KEY);
        String previousLatestMessageTs = cursorValue(cursor, LATEST_MESSAGE_TS_CURSOR_KEY);
        String latestMessageTs = previousLatestMessageTs;
        Set<String> emittedExternalIds = new HashSet<>();

        for (SlackClient.SlackMessage message : slackClient.listChannelMessages(channelId, previousLatestMessageTs)) {
            emitIfNotSeen(dataSource, message, handler, emittedExternalIds);
            latestMessageTs = newestSlackTs(latestMessageTs, message.messageTs());
            if (message.hasReplies()) {
                for (SlackClient.SlackMessage reply : slackClient.listThreadReplies(channelId, message.messageTs())) {
                    if (!message.messageTs().equals(reply.messageTs())) {
                        emitIfNotSeen(dataSource, reply, handler, emittedExternalIds);
                    }
                }
            }
        }
        return nextCursor(cursor, latestMessageTs);
    }

    private void emitIfNotSeen(
        DataSourceEntity dataSource,
        SlackClient.SlackMessage message,
        DocumentHandler handler,
        Set<String> emittedExternalIds
    ) {
        if (emittedExternalIds.add(externalId(message))) {
            handler.handle(rawDocuments.toRawDocument(dataSource, message));
        }
    }

    private String externalId(SlackClient.SlackMessage message) {
        return message.channelId() + ":" + message.messageTs();
    }

    private String requiredConfig(DataSourceEntity dataSource, String key) {
        String value = dataSource.configValue(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("SLACK 설정값이 없습니다: " + key);
        }
        return value.trim();
    }

    private String cursorValue(SyncCursor cursor, String key) {
        if (cursor == null || cursor.value() == null) {
            return null;
        }
        Object value = cursor.value().get(key);
        return value instanceof String stringValue && !stringValue.isBlank() ? stringValue.trim() : null;
    }

    private SyncCursor nextCursor(SyncCursor cursor, String latestMessageTs) {
        Map<String, Object> nextCursor = new LinkedHashMap<>();
        if (cursor != null && cursor.value() != null) {
            nextCursor.putAll(cursor.value());
        }
        nextCursor.remove("latestThreadReplyTs");
        nextCursor.remove("trackedThreadRootTs");
        if (latestMessageTs != null) {
            nextCursor.put(LATEST_MESSAGE_TS_CURSOR_KEY, latestMessageTs);
        }
        return new SyncCursor(nextCursor);
    }

    private String newestSlackTs(String current, String candidate) {
        String normalizedCandidate = blankToNull(candidate);
        if (normalizedCandidate == null) {
            return current;
        }
        String normalizedCurrent = blankToNull(current);
        if (normalizedCurrent == null || compareSlackTs(normalizedCandidate, normalizedCurrent) > 0) {
            return normalizedCandidate;
        }
        return normalizedCurrent;
    }

    private int compareSlackTs(String left, String right) {
        try {
            return new BigDecimal(left).compareTo(new BigDecimal(right));
        } catch (NumberFormatException exception) {
            return left.compareTo(right);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
