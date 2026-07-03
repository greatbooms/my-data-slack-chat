package com.mydata.connectors.slack;

import com.mydata.slackbot.SlackBotProperties;
import com.slack.api.model.Message;
import com.slack.api.model.block.SectionBlock;
import com.slack.api.model.block.composition.MarkdownTextObject;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SlackWebApiClientTest {
    @Test
    void rejectsMissingBotTokenBeforeCallingSlackApi() {
        SlackWebApiClient client = new SlackWebApiClient(new SlackBotProperties(
            "",
            "",
            " ",
            false,
            false
        ));

        assertThatThrownBy(() -> client.listChannelMessages("C123", null))
            .isInstanceOf(SlackApiException.class)
            .hasMessageContaining("SLACK_BOT_TOKEN");
    }

    @Test
    void extractsSectionBlockTextInAdditionToFallbackMessageText() {
        SlackWebApiClient client = new SlackWebApiClient(new SlackBotProperties(
            "",
            "",
            "xoxb-test",
            false,
            false
        ));
        Message message = new Message();
        message.setText("딥 분석 리포트 | 005930");
        message.setBlocks(List.of(SectionBlock.builder()
            .text(MarkdownTextObject.builder()
                .text("""
                    :bar_chart: 삼성전자 딥 분석 리포트 — 2026.07.01

                    ■ 가치평가 (DCF)
                    내재가치: 79719 | 현재가: 333750
                    """.stripTrailing())
                .build())
            .build()));

        String text = client.extractText(message);

        assertThat(text)
            .contains("딥 분석 리포트 | 005930")
            .contains("삼성전자 딥 분석 리포트")
            .contains("내재가치: 79719");
    }

    @Test
    void treatsBotMessageSubtypeWithTextAsCollectableMessage() throws Exception {
        SlackWebApiClient client = new SlackWebApiClient(new SlackBotProperties(
            "",
            "",
            "xoxb-test",
            false,
            false
        ));
        Message message = new Message();
        message.setType("message");
        message.setSubtype("bot_message");
        message.setTs("1710000000.000100");
        message.setText("앱이 게시한 분석 리포트");

        Method isTextMessage = SlackWebApiClient.class.getDeclaredMethod("isTextMessage", Message.class);
        isTextMessage.setAccessible(true);

        assertThat((Boolean) isTextMessage.invoke(client, message)).isTrue();
    }
}
