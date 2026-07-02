package com.mydata.slackbot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

@Component
public class SlackSocketModeEventHandler {
    private static final Logger log = LoggerFactory.getLogger(SlackSocketModeEventHandler.class);

    private final SlackQuestionEventConsumer questionConsumer;
    private final TaskExecutor questionExecutor;

    public SlackSocketModeEventHandler(
        SlackQuestionEventConsumer questionConsumer,
        @Qualifier("applicationTaskExecutor") TaskExecutor questionExecutor
    ) {
        this.questionConsumer = questionConsumer;
        this.questionExecutor = questionExecutor;
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

        SlackQuestionEvent event = new SlackQuestionEvent(
            source,
            teamId,
            channelId,
            userId,
            text.trim(),
            messageTimestamp,
            threadTimestamp,
            selectThreadId(messageTimestamp, threadTimestamp)
        );

        try {
            questionExecutor.execute(() -> questionConsumer.accept(event));
        } catch (RuntimeException exception) {
            log.warn(
                "Slack 질문 비동기 작업 등록에 실패했습니다. teamId={}, channelId={}, userId={}, externalThreadId={}",
                event.teamId(),
                event.channelId(),
                event.userId(),
                event.externalThreadId(),
                exception
            );
        }
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
