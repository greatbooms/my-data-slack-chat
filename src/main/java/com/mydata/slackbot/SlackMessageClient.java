package com.mydata.slackbot;

public interface SlackMessageClient {
    void postMessage(String channelId, String threadTimestamp, String text);
}
