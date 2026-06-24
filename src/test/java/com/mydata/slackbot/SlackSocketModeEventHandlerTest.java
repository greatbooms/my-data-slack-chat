package com.mydata.slackbot;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SlackSocketModeEventHandlerTest {
    @Test
    void forwardsAppMentionWithThreadTimestampWhenPresent() {
        RecordingQuestionConsumer consumer = new RecordingQuestionConsumer();
        SlackSocketModeEventHandler handler = new SlackSocketModeEventHandler(consumer);

        handler.handleAppMention("T123", "C456", "U789", "질문입니다", "1710000000.000000", "1710000001.000000");

        assertThat(consumer.events)
            .hasSize(1)
            .first()
            .satisfies(event -> {
                assertThat(event.source()).isEqualTo(SlackQuestionEvent.Source.APP_MENTION);
                assertThat(event.teamId()).isEqualTo("T123");
                assertThat(event.channelId()).isEqualTo("C456");
                assertThat(event.userId()).isEqualTo("U789");
                assertThat(event.text()).isEqualTo("질문입니다");
                assertThat(event.externalThreadId()).isEqualTo("1710000001.000000");
            });
    }

    @Test
    void usesMessageTimestampAsThreadIdWhenThreadTimestampIsMissing() {
        RecordingQuestionConsumer consumer = new RecordingQuestionConsumer();
        SlackSocketModeEventHandler handler = new SlackSocketModeEventHandler(consumer);

        handler.handleDirectMessage("T123", "D456", "U789", "DM 질문", "1710000000.000000", " ");

        assertThat(consumer.events)
            .hasSize(1)
            .first()
            .satisfies(event -> {
                assertThat(event.source()).isEqualTo(SlackQuestionEvent.Source.DIRECT_MESSAGE);
                assertThat(event.externalThreadId()).isEqualTo("1710000000.000000");
            });
    }

    @Test
    void ignoresBlankQuestionText() {
        RecordingQuestionConsumer consumer = new RecordingQuestionConsumer();
        SlackSocketModeEventHandler handler = new SlackSocketModeEventHandler(consumer);

        handler.handleAppMention("T123", "C456", "U789", " ", "1710000000.000000", null);

        assertThat(consumer.events).isEmpty();
    }

    private static class RecordingQuestionConsumer implements SlackQuestionEventConsumer {
        List<SlackQuestionEvent> events = new ArrayList<>();

        @Override
        public void accept(SlackQuestionEvent event) {
            events.add(event);
        }
    }
}
