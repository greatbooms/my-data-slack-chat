package com.mydata.slackbot;

public record SlackQuestionEvent(
    Source source,
    String teamId,
    String channelId,
    String userId,
    String text,
    String messageTimestamp,
    String threadTimestamp,
    String externalThreadId
) {
    public enum Source {
        APP_MENTION,
        DIRECT_MESSAGE
    }
}
