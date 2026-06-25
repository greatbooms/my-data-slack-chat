package com.mydata.slackbot;

import com.slack.api.model.event.AppMentionEvent;
import com.slack.api.model.event.MessageEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BoltSlackSocketModeClientFactoryTest {
    @Test
    void stripsLeadingBotMentionFromAppMentionText() {
        String text = BoltSlackSocketModeClientFactory.stripLeadingBotMention("<@U999>   질문입니다", "U999");

        assertThat(text).isEqualTo("질문입니다");
    }

    @Test
    void stripsLeadingBotMentionWithDisplayLabelFromAppMentionText() {
        String text = BoltSlackSocketModeClientFactory.stripLeadingBotMention("<@U999|my-data> 질문입니다", "U999");

        assertThat(text).isEqualTo("질문입니다");
    }

    @Test
    void keepsAppMentionTextWhenMentionDoesNotMatchBotUser() {
        String text = BoltSlackSocketModeClientFactory.stripLeadingBotMention("<@U123> 질문입니다", "U999");

        assertThat(text).isEqualTo("<@U123> 질문입니다");
    }

    @Test
    void rejectsBotAppMentionEvents() {
        AppMentionEvent event = new AppMentionEvent();
        event.setUser("U123");
        event.setBotId("B123");
        event.setText("<@U999> 질문입니다");

        assertThat(BoltSlackSocketModeClientFactory.isUserAppMention(event)).isFalse();
    }

    @Test
    void rejectsSubtypeAppMentionEvents() {
        AppMentionEvent event = new AppMentionEvent();
        event.setUser("U123");
        event.setSubtype("bot_message");
        event.setText("<@U999> 질문입니다");

        assertThat(BoltSlackSocketModeClientFactory.isUserAppMention(event)).isFalse();
    }

    @Test
    void acceptsPlainUserAppMentionEvents() {
        AppMentionEvent event = new AppMentionEvent();
        event.setUser("U123");
        event.setText("<@U999> 질문입니다");

        assertThat(BoltSlackSocketModeClientFactory.isUserAppMention(event)).isTrue();
    }

    @Test
    void rejectsEditedDirectMessages() {
        MessageEvent event = new MessageEvent();
        event.setChannelType("im");
        event.setUser("U123");
        event.setEdited(new MessageEvent.Edited());

        assertThat(BoltSlackSocketModeClientFactory.isDirectUserMessage(event)).isFalse();
    }
}
