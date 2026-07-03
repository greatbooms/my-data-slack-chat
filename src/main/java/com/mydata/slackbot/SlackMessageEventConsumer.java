package com.mydata.slackbot;

public interface SlackMessageEventConsumer {
    void accept(SlackMessageIngestionEvent event);
}
