package com.mydata.slackbot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingSlackQuestionEventConsumer implements SlackQuestionEventConsumer {
    private static final Logger log = LoggerFactory.getLogger(LoggingSlackQuestionEventConsumer.class);

    @Override
    public void accept(SlackQuestionEvent event) {
        log.info(
            "Slack 질문 이벤트를 수신했습니다. source={}, teamId={}, channelId={}, userId={}, externalThreadId={}",
            event.source(),
            event.teamId(),
            event.channelId(),
            event.userId(),
            event.externalThreadId()
        );
    }
}
