package com.mydata.slackbot;

import com.slack.api.bolt.App;
import com.slack.api.bolt.AppConfig;
import com.slack.api.bolt.jakarta_socket_mode.SocketModeApp;
import com.slack.api.model.event.AppMentionEvent;
import com.slack.api.model.event.MessageEvent;
import org.springframework.stereotype.Component;

@Component
public class BoltSlackSocketModeClientFactory implements SlackSocketModeClientFactory {
    private final SlackSocketModeEventHandler eventHandler;

    public BoltSlackSocketModeClientFactory(SlackSocketModeEventHandler eventHandler) {
        this.eventHandler = eventHandler;
    }

    @Override
    public SlackSocketModeClient create(String appToken, String botToken) {
        App app = new App(AppConfig.builder()
            .singleTeamBotToken(botToken)
            .build());

        app.event(AppMentionEvent.class, (payload, context) -> {
            AppMentionEvent event = payload.getEvent();
            eventHandler.handleAppMention(
                firstNonBlank(payload.getTeamId(), event.getTeam()),
                event.getChannel(),
                event.getUser(),
                event.getText(),
                event.getTs(),
                event.getThreadTs()
            );
            return context.ack();
        });

        app.event(MessageEvent.class, (payload, context) -> {
            MessageEvent event = payload.getEvent();
            if (isDirectUserMessage(event)) {
                eventHandler.handleDirectMessage(
                    firstNonBlank(payload.getTeamId(), event.getTeam()),
                    event.getChannel(),
                    event.getUser(),
                    event.getText(),
                    event.getTs(),
                    event.getThreadTs()
                );
            }
            return context.ack();
        });

        return new BoltSlackSocketModeClient(appToken, app);
    }

    private static boolean isDirectUserMessage(MessageEvent event) {
        return "im".equals(event.getChannelType())
            && !isBlank(event.getUser())
            && isBlank(event.getBotId());
    }

    private static String firstNonBlank(String first, String second) {
        if (!isBlank(first)) {
            return first;
        }
        return second;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static final class BoltSlackSocketModeClient implements SlackSocketModeClient {
        private final String appToken;
        private final App app;
        private SocketModeApp socketModeApp;

        private BoltSlackSocketModeClient(String appToken, App app) {
            this.appToken = appToken;
            this.app = app;
        }

        @Override
        public void start() throws Exception {
            socketModeApp = new SocketModeApp(appToken, app);
            socketModeApp.startAsync();
        }

        @Override
        public void stop() throws Exception {
            if (socketModeApp != null) {
                socketModeApp.stop();
            }
        }
    }
}
