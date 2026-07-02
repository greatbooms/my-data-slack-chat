package com.mydata.slackbot;

import com.slack.api.Slack;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import org.springframework.stereotype.Component;

@Component
public class SlackWebApiMessageClient implements SlackMessageClient {
    private final SlackBotProperties properties;

    public SlackWebApiMessageClient(SlackBotProperties properties) {
        this.properties = properties;
    }

    @Override
    public void postMessage(String channelId, String threadTimestamp, String text) {
        if (isBlank(channelId)) {
            throw new SlackMessagePostException("Slack channelId 값은 비어 있을 수 없습니다");
        }
        if (isBlank(threadTimestamp)) {
            throw new SlackMessagePostException("Slack threadTimestamp 값은 비어 있을 수 없습니다");
        }
        if (isBlank(properties.botToken())) {
            throw new SlackMessagePostException("Slack bot token 값은 비어 있을 수 없습니다");
        }

        try {
            ChatPostMessageResponse response = Slack.getInstance()
                .methods(properties.botToken())
                .chatPostMessage(request -> request
                    .channel(channelId)
                    .threadTs(threadTimestamp)
                    .text(text));
            if (!response.isOk()) {
                throw new SlackMessagePostException("Slack 메시지 전송에 실패했습니다: " + response.getError());
            }
        } catch (SlackMessagePostException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new SlackMessagePostException("Slack 메시지 전송 중 오류가 발생했습니다", exception);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
