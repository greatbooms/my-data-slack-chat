package com.mydata.slackbot;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "my-data.slack")
public record SlackBotProperties(
    String signingSecret,
    String appToken,
    String botToken,
    boolean socketModeEnabled,
    boolean httpEventsEnabled
) {
}
