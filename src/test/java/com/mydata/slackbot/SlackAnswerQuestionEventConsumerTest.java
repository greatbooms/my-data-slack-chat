package com.mydata.slackbot;

import com.mydata.auth.PrincipalKeys;
import com.mydata.chat.ChatApplicationService;
import com.mydata.identities.ExternalIdentityEntity;
import com.mydata.identities.ExternalIdentityProvider;
import com.mydata.identities.ExternalIdentityRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SlackAnswerQuestionEventConsumerTest {
    @Test
    void answersMappedSlackQuestionInOriginalThread() {
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ExternalIdentityEntity identity = ExternalIdentityEntity.create(
            ExternalIdentityProvider.SLACK,
            workspaceId,
            userId,
            "T123",
            "U123",
            "slack@example.com",
            "Slack User"
        );
        ExternalIdentityRepository identities = mock(ExternalIdentityRepository.class);
        ChatApplicationService chat = mock(ChatApplicationService.class);
        RecordingSlackMessageClient slackMessages = new RecordingSlackMessageClient();
        SlackAnswerQuestionEventConsumer consumer = new SlackAnswerQuestionEventConsumer(
            identities,
            chat,
            slackMessages
        );
        SlackQuestionEvent event = new SlackQuestionEvent(
            SlackQuestionEvent.Source.APP_MENTION,
            "T123",
            "C123",
            "U123",
            "alpha budget?",
            "1710000000.000000",
            null,
            "1710000000.000000"
        );
        List<String> expectedPrincipals = List.of(
            PrincipalKeys.user(userId),
            PrincipalKeys.workspace(workspaceId),
            PrincipalKeys.slackUser("T123", "U123")
        );
        UUID chunkId = UUID.randomUUID();

        when(identities.findByProviderAndExternalWorkspaceIdAndExternalUserId(
            ExternalIdentityProvider.SLACK,
            "T123",
            "U123"
        )).thenReturn(Optional.of(identity));
        when(chat.answer(
            workspaceId,
            userId,
            "SLACK",
            "C123",
            "1710000000.000000",
            expectedPrincipals,
            "alpha budget?"
        )).thenReturn(new ChatApplicationService.Answer(
            UUID.randomUUID(),
            "검색된 근거를 찾았습니다. 출처: Budget note",
            List.of(new ChatApplicationService.Citation(
                chunkId,
                "Budget note",
                "local://budget-note",
                "LOCAL_TEXT",
                1,
                0.12
            ))
        ));

        consumer.accept(event);

        assertThat(slackMessages.messages())
            .singleElement()
            .satisfies(message -> {
                assertThat(message.channelId()).isEqualTo("C123");
                assertThat(message.threadTimestamp()).isEqualTo("1710000000.000000");
                assertThat(message.text()).contains("검색된 근거를 찾았습니다.");
                assertThat(message.text()).contains("출처");
                assertThat(message.text()).contains("Budget note");
            });
    }

    @Test
    void postsMappingRequiredMessageWhenSlackUserIsUnknown() {
        ExternalIdentityRepository identities = mock(ExternalIdentityRepository.class);
        ChatApplicationService chat = mock(ChatApplicationService.class);
        RecordingSlackMessageClient slackMessages = new RecordingSlackMessageClient();
        SlackAnswerQuestionEventConsumer consumer = new SlackAnswerQuestionEventConsumer(
            identities,
            chat,
            slackMessages
        );
        SlackQuestionEvent event = new SlackQuestionEvent(
            SlackQuestionEvent.Source.DIRECT_MESSAGE,
            "T999",
            "D123",
            "U999",
            "what can I read?",
            "1710000001.000000",
            null,
            "1710000001.000000"
        );

        when(identities.findByProviderAndExternalWorkspaceIdAndExternalUserId(
            ExternalIdentityProvider.SLACK,
            "T999",
            "U999"
        )).thenReturn(Optional.empty());

        consumer.accept(event);

        verify(chat, never()).answer(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()
        );
        assertThat(slackMessages.messages())
            .singleElement()
            .satisfies(message -> {
                assertThat(message.channelId()).isEqualTo("D123");
                assertThat(message.threadTimestamp()).isEqualTo("1710000001.000000");
                assertThat(message.text()).contains("Slack 계정 매핑");
            });
    }

    private static final class RecordingSlackMessageClient implements SlackMessageClient {
        private final List<PostedMessage> messages = new ArrayList<>();

        @Override
        public void postMessage(String channelId, String threadTimestamp, String text) {
            messages.add(new PostedMessage(channelId, threadTimestamp, text));
        }

        List<PostedMessage> messages() {
            return messages;
        }
    }

    private record PostedMessage(String channelId, String threadTimestamp, String text) {
    }
}
