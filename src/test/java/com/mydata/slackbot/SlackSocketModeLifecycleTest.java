package com.mydata.slackbot;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlackSocketModeLifecycleTest {
    @Test
    void doesNotStartWhenSocketModeIsDisabled() {
        RecordingClientFactory factory = new RecordingClientFactory();
        SlackSocketModeLifecycle lifecycle = new SlackSocketModeLifecycle(
            new SlackBotProperties("signing-secret", "xapp-token", "xoxb-token", false, false),
            factory
        );

        lifecycle.start();

        assertThat(factory.created).isFalse();
        assertThat(lifecycle.isRunning()).isFalse();
    }

    @Test
    void doesNotStartWhenTokensAreMissing() {
        RecordingClientFactory factory = new RecordingClientFactory();
        SlackSocketModeLifecycle lifecycle = new SlackSocketModeLifecycle(
            new SlackBotProperties("signing-secret", " ", "", true, false),
            factory
        );

        lifecycle.start();

        assertThat(factory.created).isFalse();
        assertThat(lifecycle.isRunning()).isFalse();
    }

    @Test
    void startsAndStopsSocketModeClientWhenEnabled() {
        RecordingClientFactory factory = new RecordingClientFactory();
        SlackSocketModeLifecycle lifecycle = new SlackSocketModeLifecycle(
            new SlackBotProperties("signing-secret", "xapp-token", "xoxb-token", true, false),
            factory
        );

        lifecycle.start();

        assertThat(factory.created).isTrue();
        assertThat(factory.appToken).isEqualTo("xapp-token");
        assertThat(factory.botToken).isEqualTo("xoxb-token");
        assertThat(factory.client.started).isTrue();
        assertThat(lifecycle.isRunning()).isTrue();

        lifecycle.stop();

        assertThat(factory.client.stopped).isTrue();
        assertThat(lifecycle.isRunning()).isFalse();
    }

    private static class RecordingClientFactory implements SlackSocketModeClientFactory {
        boolean created;
        String appToken;
        String botToken;
        RecordingClient client = new RecordingClient();

        @Override
        public SlackSocketModeClient create(String appToken, String botToken) {
            this.created = true;
            this.appToken = appToken;
            this.botToken = botToken;
            return client;
        }
    }

    private static class RecordingClient implements SlackSocketModeClient {
        boolean started;
        boolean stopped;

        @Override
        public void start() {
            started = true;
        }

        @Override
        public void stop() {
            stopped = true;
        }
    }
}
