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

    @Test
    void acceptsChannelMessagesForDataIngestion() {
        MessageEvent event = new MessageEvent();
        event.setChannelType("channel");
        event.setChannel("C123");
        event.setUser("U123");
        event.setText("수집할 메시지");
        event.setTs("1710000000.000000");

        assertThat(BoltSlackSocketModeClientFactory.isCollectableChannelMessage(event, "U999")).isTrue();
    }

    @Test
    void acceptsPrivateChannelMessagesForDataIngestion() {
        MessageEvent event = new MessageEvent();
        event.setChannelType("group");
        event.setChannel("G123");
        event.setUser("U123");
        event.setText("수집할 비공개 채널 메시지");
        event.setTs("1710000000.000000");

        assertThat(BoltSlackSocketModeClientFactory.isCollectableChannelMessage(event, "U999")).isTrue();
    }

    @Test
    void rejectsDirectMessagesForDataIngestion() {
        MessageEvent event = new MessageEvent();
        event.setChannelType("im");
        event.setChannel("D123");
        event.setUser("U123");
        event.setText("DM");
        event.setTs("1710000000.000000");

        assertThat(BoltSlackSocketModeClientFactory.isCollectableChannelMessage(event, "U999")).isFalse();
    }

    @Test
    void rejectsOwnBotMessagesForDataIngestion() {
        MessageEvent event = new MessageEvent();
        event.setChannelType("channel");
        event.setChannel("C123");
        event.setUser("U999");
        event.setBotId("B999");
        event.setText("봇 답변");
        event.setTs("1710000000.000000");

        assertThat(BoltSlackSocketModeClientFactory.isCollectableChannelMessage(event, "U999")).isFalse();
    }

    @Test
    void rejectsOwnBotMessagesForDataIngestionByBotId() {
        MessageEvent event = new MessageEvent();
        event.setChannelType("channel");
        event.setChannel("C123");
        event.setBotId("B999");
        event.setText("봇 답변");
        event.setTs("1710000000.000000");

        assertThat(BoltSlackSocketModeClientFactory.isCollectableChannelMessage(event, "U999", "B999")).isFalse();
    }

    @Test
    void acceptsOtherBotMessagesForDataIngestion() {
        MessageEvent event = new MessageEvent();
        event.setChannelType("channel");
        event.setChannel("C123");
        event.setBotId("B123");
        event.setText("다른 앱이 게시한 리포트");
        event.setTs("1710000000.000000");

        assertThat(BoltSlackSocketModeClientFactory.isCollectableChannelMessage(event, "U999", "B999")).isTrue();
    }

    @Test
    void rejectsEditedChannelMessagesForDataIngestion() {
        MessageEvent event = new MessageEvent();
        event.setChannelType("channel");
        event.setChannel("C123");
        event.setUser("U123");
        event.setText("수정 메시지");
        event.setTs("1710000000.000000");
        event.setEdited(new MessageEvent.Edited());

        assertThat(BoltSlackSocketModeClientFactory.isCollectableChannelMessage(event, "U999")).isFalse();
    }
}
