package com.mydata.slackbot;

import org.springframework.stereotype.Component;

@Component
public class SlackSocketModeEventHandler {
    private final SlackQuestionEventConsumer questionConsumer;

    public SlackSocketModeEventHandler(SlackQuestionEventConsumer questionConsumer) {
        this.questionConsumer = questionConsumer;
    }

    public void handleAppMention(
        String teamId,
        String channelId,
        String userId,
        String text,
        String messageTimestamp,
        String threadTimestamp
    ) {
        handle(
            SlackQuestionEvent.Source.APP_MENTION,
            teamId,
            channelId,
            userId,
            text,
            messageTimestamp,
            threadTimestamp
        );
    }

    public void handleDirectMessage(
        String teamId,
        String channelId,
        String userId,
        String text,
        String messageTimestamp,
        String threadTimestamp
    ) {
        handle(
            SlackQuestionEvent.Source.DIRECT_MESSAGE,
            teamId,
            channelId,
            userId,
            text,
            messageTimestamp,
            threadTimestamp
        );
    }

    private void handle(
        SlackQuestionEvent.Source source,
        String teamId,
        String channelId,
        String userId,
        String text,
        String messageTimestamp,
        String threadTimestamp
    ) {
        if (isBlank(text)) {
            return;
        }

        questionConsumer.accept(new SlackQuestionEvent(
            source,
            teamId,
            channelId,
            userId,
            text.trim(),
            messageTimestamp,
            threadTimestamp,
            selectThreadId(messageTimestamp, threadTimestamp)
        ));
    }

    private static String selectThreadId(String messageTimestamp, String threadTimestamp) {
        if (!isBlank(threadTimestamp)) {
            return threadTimestamp;
        }
        return messageTimestamp;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
