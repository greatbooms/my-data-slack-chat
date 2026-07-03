package com.mydata.slackbot;

public record SlackMessageIngestionEvent(
    String teamId,
    String channelId,
    String userId,
    String text,
    String messageTimestamp,
    String threadTimestamp,
    String channelType
) {
}
