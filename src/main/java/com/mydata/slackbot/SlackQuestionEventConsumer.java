package com.mydata.slackbot;

public interface SlackQuestionEventConsumer {
    void accept(SlackQuestionEvent event);
}
