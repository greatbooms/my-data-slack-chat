package com.mydata.chat;

import com.mydata.retrieval.RetrievalService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatApplicationServiceRaceTest {
    @Test
    void reloadsSessionWhenConcurrentCreateWinsRace() {
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String channelType = "SLACK";
        String externalChannelId = "C123";
        String externalThreadId = "1710000000.000000";
        List<String> principalKeys = List.of("USER:" + userId);
        ChatSessionEntity existingSession = ChatSessionEntity.create(
            workspaceId,
            channelType,
            externalChannelId,
            externalThreadId,
            userId
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

        when(sessions.findByChannel(workspaceId, channelType, externalChannelId, externalThreadId))
            .thenReturn(Optional.empty(), Optional.of(existingSession));
        when(sessions.save(any(ChatSessionEntity.class)))
            .thenThrow(new DataIntegrityViolationException("duplicate session"));
        when(messages.save(any(ChatMessageEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(retrieval.retrieve(workspaceId, principalKeys, "question", 5))
            .thenReturn(List.of());
        when(llm.generate("question", List.of()))
            .thenReturn("answer");

        ChatApplicationService.Answer answer = service.answer(
            workspaceId,
            userId,
            channelType,
            externalChannelId,
            externalThreadId,
            principalKeys,
            "question"
        );

        assertThat(answer.sessionId()).isEqualTo(existingSession.getId());
        verify(sessions, times(2)).findByChannel(workspaceId, channelType, externalChannelId, externalThreadId);
        verify(messages, times(2)).save(any(ChatMessageEntity.class));
    }
}
