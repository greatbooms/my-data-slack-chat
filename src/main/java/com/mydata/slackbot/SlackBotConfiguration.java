package com.mydata.slackbot;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SlackBotProperties.class)
class SlackBotConfiguration {
}
