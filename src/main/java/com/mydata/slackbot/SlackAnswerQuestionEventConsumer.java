package com.mydata.slackbot;

import com.mydata.auth.PrincipalKeys;
import com.mydata.chat.ChatApplicationService;
import com.mydata.identities.ExternalIdentityEntity;
import com.mydata.identities.ExternalIdentityProvider;
import com.mydata.identities.ExternalIdentityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SlackAnswerQuestionEventConsumer implements SlackQuestionEventConsumer {
    private static final Logger log = LoggerFactory.getLogger(SlackAnswerQuestionEventConsumer.class);
    private static final String CHANNEL_TYPE = "SLACK";
    private static final String MAPPING_REQUIRED_MESSAGE =
        "이 Slack 계정 매핑을 찾지 못했습니다. 관리자 화면의 외부 계정에서 Slack 팀 ID와 유저 ID를 내부 유저에 연결해 주세요.";
    private static final String ANSWER_FAILED_MESSAGE =
        "답변 처리 중 오류가 발생했습니다. 관리자에게 서버 로그 확인을 요청해 주세요.";

    private final ExternalIdentityRepository externalIdentities;
    private final ChatApplicationService chat;
    private final SlackMessageClient slackMessages;

    public SlackAnswerQuestionEventConsumer(
        ExternalIdentityRepository externalIdentities,
        ChatApplicationService chat,
        SlackMessageClient slackMessages
    ) {
        this.externalIdentities = externalIdentities;
        this.chat = chat;
        this.slackMessages = slackMessages;
    }

    @Override
    public void accept(SlackQuestionEvent event) {
        if (event == null || isBlank(event.text())) {
            return;
        }

        try {
            answer(event);
        } catch (RuntimeException exception) {
            log.warn(
                "Slack 질문 처리 중 오류가 발생했습니다. teamId={}, channelId={}, userId={}, externalThreadId={}",
                event.teamId(),
                event.channelId(),
                event.userId(),
                event.externalThreadId(),
                exception
            );
            postSafely(event.channelId(), event.externalThreadId(), ANSWER_FAILED_MESSAGE);
        }
    }

    private void answer(SlackQuestionEvent event) {
        ExternalIdentityEntity identity = externalIdentities.findByProviderAndExternalWorkspaceIdAndExternalUserId(
                ExternalIdentityProvider.SLACK,
                event.teamId(),
                event.userId()
            )
            .orElse(null);
        if (identity == null) {
            log.info(
                "Slack 외부 계정 매핑을 찾지 못했습니다. teamId={}, userId={}",
                event.teamId(),
                event.userId()
            );
            slackMessages.postMessage(event.channelId(), event.externalThreadId(), MAPPING_REQUIRED_MESSAGE);
            return;
        }

        ChatApplicationService.Answer answer = chat.answer(
            identity.getWorkspaceId(),
            identity.getUserId(),
            CHANNEL_TYPE,
            event.channelId(),
            event.externalThreadId(),
            principalKeys(identity),
            event.text()
        );
        slackMessages.postMessage(event.channelId(), event.externalThreadId(), formatAnswer(answer));
    }

    private static List<String> principalKeys(ExternalIdentityEntity identity) {
        List<String> principalKeys = new ArrayList<>();
        principalKeys.add(PrincipalKeys.user(identity.getUserId()));
        principalKeys.add(PrincipalKeys.workspace(identity.getWorkspaceId()));
        principalKeys.add(identity.getPrincipalKey());
        return List.copyOf(principalKeys);
    }

    private static String formatAnswer(ChatApplicationService.Answer answer) {
        StringBuilder message = new StringBuilder(answer.content());
        if (!answer.citations().isEmpty()) {
            message.append("\n\n출처");
            answer.citations().forEach(citation -> message
                .append("\n")
                .append(citation.rank())
                .append(". ")
                .append(formatCitation(citation)));
        }
        return message.toString();
    }

    private static String formatCitation(ChatApplicationService.Citation citation) {
        String title = isBlank(citation.title()) ? citation.sourceType() : citation.title();
        if (isBlank(citation.uri())) {
            return title;
        }
        return "<" + citation.uri() + "|" + title + ">";
    }

    private void postSafely(String channelId, String threadTimestamp, String text) {
        try {
            slackMessages.postMessage(channelId, threadTimestamp, text);
        } catch (RuntimeException postException) {
            log.warn("Slack 오류 안내 메시지 전송에도 실패했습니다.", postException);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
