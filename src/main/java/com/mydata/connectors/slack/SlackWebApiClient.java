package com.mydata.connectors.slack;

import com.mydata.slackbot.SlackBotProperties;
import com.slack.api.Slack;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.response.conversations.ConversationsHistoryResponse;
import com.slack.api.methods.response.conversations.ConversationsRepliesResponse;
import com.slack.api.model.Message;
import com.slack.api.model.ResponseMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class SlackWebApiClient implements SlackClient {
    private static final int PAGE_LIMIT = 100;

    private final SlackBotProperties properties;
    private final SlackMessageTextExtractor textExtractor;

    public SlackWebApiClient(SlackBotProperties properties) {
        this(properties, new SlackMessageTextExtractor());
    }

    @Autowired
    public SlackWebApiClient(SlackBotProperties properties, SlackMessageTextExtractor textExtractor) {
        this.properties = properties;
        this.textExtractor = textExtractor;
    }

    @Override
    public List<SlackMessage> listChannelMessages(String channelId, String oldestMessageTs) {
        requireBotToken();
        List<Message> messages = new ArrayList<>();
        String cursor = null;
        do {
            String currentCursor = cursor;
            ConversationsHistoryResponse response = call(() -> methods().conversationsHistory(request -> request
                .channel(channelId)
                .limit(PAGE_LIMIT)
                .oldest(blankToNull(oldestMessageTs))
                .inclusive(false)
                .cursor(currentCursor)));
            if (!response.isOk()) {
                throw new SlackApiException("Slack conversations.history 호출 실패: " + error(response.getError()));
            }
            messages.addAll(response.getMessages() == null ? List.of() : response.getMessages());
            cursor = nextCursor(response.getResponseMetadata());
        } while (cursor != null);
        return messages.stream()
            .filter(this::isTextMessage)
            .map(message -> toSlackMessage(channelId, message))
            .toList();
    }

    @Override
    public List<SlackMessage> listThreadReplies(String channelId, String threadTs) {
        requireBotToken();
        List<Message> messages = new ArrayList<>();
        String cursor = null;
        do {
            String currentCursor = cursor;
            ConversationsRepliesResponse response = call(() -> methods().conversationsReplies(request -> request
                .channel(channelId)
                .ts(threadTs)
                .limit(PAGE_LIMIT)
                .cursor(currentCursor)));
            if (!response.isOk()) {
                throw new SlackApiException("Slack conversations.replies 호출 실패: " + error(response.getError()));
            }
            messages.addAll(response.getMessages() == null ? List.of() : response.getMessages());
            cursor = nextCursor(response.getResponseMetadata());
        } while (cursor != null);
        return messages.stream()
            .filter(this::isTextMessage)
            .map(message -> toSlackMessage(channelId, message))
            .toList();
    }

    private SlackMessage toSlackMessage(String channelId, Message message) {
        String messageTs = message.getTs();
        String threadTs = blankToNull(message.getThreadTs());
        boolean threadReply = threadTs != null && !threadTs.equals(messageTs);
        return new SlackMessage(
            channelId,
            messageTs,
            threadTs == null ? messageTs : threadTs,
            firstNonBlank(message.getUser(), message.getBotId(), message.getUsername()),
            extractText(message),
            instantFromSlackTs(messageTs),
            threadReply,
            hasReplies(message),
            null
        );
    }

    private boolean isTextMessage(Message message) {
        return message != null
            && "message".equals(message.getType())
            && !isNonContentSubtype(message.getSubtype())
            && blankToNull(message.getTs()) != null
            && blankToNull(extractText(message)) != null;
    }

    private boolean isNonContentSubtype(String subtype) {
        String normalized = blankToNull(subtype);
        return normalized != null && Set.of(
            "channel_join",
            "channel_leave",
            "channel_name",
            "channel_purpose",
            "channel_topic",
            "group_join",
            "group_leave",
            "message_deleted"
        ).contains(normalized);
    }

    private boolean hasReplies(Message message) {
        return message.getReplyCount() != null && message.getReplyCount() > 0;
    }

    private MethodsClient methods() {
        return Slack.getInstance().methods(properties.botToken());
    }

    String extractText(Message message) {
        return textExtractor.extract(message);
    }

    private void requireBotToken() {
        if (properties.botToken() == null || properties.botToken().isBlank()) {
            throw new SlackApiException("SLACK_BOT_TOKEN이 필요합니다");
        }
    }

    private <T> T call(SlackCall<T> call) {
        try {
            return call.execute();
        } catch (IOException | com.slack.api.methods.SlackApiException exception) {
            throw new SlackApiException("Slack API 호출 중 오류가 발생했습니다", exception);
        }
    }

    private String nextCursor(ResponseMetadata metadata) {
        if (metadata == null || metadata.getNextCursor() == null || metadata.getNextCursor().isBlank()) {
            return null;
        }
        return metadata.getNextCursor();
    }

    private Instant instantFromSlackTs(String ts) {
        if (ts == null || ts.isBlank()) {
            return Instant.EPOCH;
        }
        String[] parts = ts.split("\\.", 2);
        long seconds = Long.parseLong(parts[0]);
        long nanos = 0L;
        if (parts.length == 2) {
            String fraction = (parts[1] + "000000000").substring(0, 9);
            nanos = Long.parseLong(fraction);
        }
        return Instant.ofEpochSecond(seconds, nanos);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = blankToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String error(String error) {
        return error == null || error.isBlank() ? "unknown" : error;
    }

    @FunctionalInterface
    private interface SlackCall<T> {
        T execute() throws IOException, com.slack.api.methods.SlackApiException;
    }
}
