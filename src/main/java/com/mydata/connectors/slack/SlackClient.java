package com.mydata.connectors.slack;

import java.time.Instant;
import java.util.List;

public interface SlackClient {
    List<SlackMessage> listChannelMessages(String channelId, String oldestMessageTs);

    List<SlackMessage> listThreadReplies(String channelId, String threadTs);

    record SlackMessage(
        String channelId,
        String messageTs,
        String threadTs,
        String userId,
        String text,
        Instant createdAt,
        boolean threadReply,
        boolean hasReplies,
        String permalink
    ) {
    }
}
