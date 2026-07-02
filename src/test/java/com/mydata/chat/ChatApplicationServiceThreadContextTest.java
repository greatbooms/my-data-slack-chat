package com.mydata.chat;

import com.mydata.retrieval.RetrievalService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatApplicationServiceThreadContextTest {
    @Test
    void followUpQuestionUsesPreviousThreadMessagesForRetrievalAndLlmContext() {
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        List<String> principalKeys = List.of("USER:" + userId);
        ChatSessionEntity session = ChatSessionEntity.create(
            workspaceId,
            "SLACK",
            "C123",
            "1710000000.000000",
            userId
        );
        ChatMessageEntity previousQuestion = ChatMessageEntity.create(
            session.getId(),
            "USER",
            "지금 노션 페이지 목록에 어떤게 있어?"
        );
        ChatMessageEntity previousAnswer = ChatMessageEntity.create(
            session.getId(),
            "ASSISTANT",
            "현재 검색 근거로 확인되는 노션 페이지 목록은 20260630, 메모, meeting, 할일입니다."
        );
        ChatSessionRepository sessions = mock(ChatSessionRepository.class);
        ChatMessageRepository messages = mock(ChatMessageRepository.class);
        ChatRetrievalCitationRepository citations = mock(ChatRetrievalCitationRepository.class);
        RetrievalService retrieval = mock(RetrievalService.class);
        LlmClient llm = mock(LlmClient.class);
        ChatApplicationService service = new ChatApplicationService(
            sessions,
            messages,
            citations,
            retrieval,
            llm
        );

        when(sessions.findByChannel(workspaceId, "SLACK", "C123", "1710000000.000000"))
            .thenReturn(Optional.of(session));
        when(messages.findBySessionIdOrderByCreatedAt(session.getId()))
            .thenReturn(List.of(previousQuestion, previousAnswer));
        when(messages.save(any(ChatMessageEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(retrieval.retrieve(eq(workspaceId), eq(principalKeys), anyString(), eq(5)))
            .thenReturn(List.of());
        when(llm.generate(eq("이거 뎁스별로 이야기해줘"), eq(List.of()), anyList()))
            .thenReturn("뎁스별 답변");

        service.answer(
            workspaceId,
            userId,
            "SLACK",
            "C123",
            "1710000000.000000",
            principalKeys,
            "이거 뎁스별로 이야기해줘"
        );

        ArgumentCaptor<String> retrievalQuery = ArgumentCaptor.forClass(String.class);
        verify(retrieval).retrieve(eq(workspaceId), eq(principalKeys), retrievalQuery.capture(), eq(5));
        assertThat(retrievalQuery.getValue())
            .contains("지금 노션 페이지 목록")
            .contains("20260630, 메모, meeting, 할일")
            .contains("이거 뎁스별로 이야기해줘");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatContextMessage>> context = ArgumentCaptor.forClass(List.class);
        verify(llm).generate(eq("이거 뎁스별로 이야기해줘"), eq(List.of()), context.capture());
        assertThat(context.getValue())
            .extracting(ChatContextMessage::role)
            .containsExactly("USER", "ASSISTANT");
        assertThat(context.getValue())
            .extracting(ChatContextMessage::content)
            .containsExactly(
                "지금 노션 페이지 목록에 어떤게 있어?",
                "현재 검색 근거로 확인되는 노션 페이지 목록은 20260630, 메모, meeting, 할일입니다."
            );
    }
}
