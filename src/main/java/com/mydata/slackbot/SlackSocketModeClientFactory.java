package com.mydata.slackbot;

public interface SlackSocketModeClientFactory {
    SlackSocketModeClient create(String appToken, String botToken);
}
