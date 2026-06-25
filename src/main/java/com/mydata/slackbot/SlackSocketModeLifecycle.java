package com.mydata.slackbot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Component
public class SlackSocketModeLifecycle implements SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(SlackSocketModeLifecycle.class);

    private final SlackBotProperties properties;
    private final SlackSocketModeClientFactory clientFactory;
    private SlackSocketModeClient client;
    private boolean running;

    public SlackSocketModeLifecycle(
        SlackBotProperties properties,
        SlackSocketModeClientFactory clientFactory
    ) {
        this.properties = properties;
        this.clientFactory = clientFactory;
    }

    @Override
    public void start() {
        if (!properties.socketModeEnabled()) {
            log.info("Slack Socket Mode가 비활성화되어 시작하지 않습니다.");
            return;
        }
        if (isBlank(properties.appToken()) || isBlank(properties.botToken())) {
            log.warn("Slack Socket Mode 토큰이 없어 시작하지 않습니다.");
            return;
        }

        try {
            client = clientFactory.create(properties.appToken(), properties.botToken());
            client.start();
            running = true;
        } catch (Exception e) {
            running = false;
            throw new IllegalStateException("Slack Socket Mode를 시작하지 못했습니다", e);
        }
    }

    @Override
    public void stop() {
        if (client == null) {
            running = false;
            return;
        }

        try {
            client.stop();
        } catch (Exception e) {
            log.warn("Slack Socket Mode 종료 중 오류가 발생했습니다.", e);
        } finally {
            running = false;
            client = null;
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
