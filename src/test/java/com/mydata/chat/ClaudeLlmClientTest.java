package com.mydata.chat;

import com.mydata.retrieval.RetrievedChunk;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClaudeLlmClientTest {
    @Test
    void usesClaudeSpecificModelWhenGeneratingAnswer() {
        LlmProperties llmProperties = new LlmProperties(
            "claude",
            5,
            1200,
            321,
            Duration.ofSeconds(5),
            Duration.ofSeconds(30)
        );
        ClaudeProperties claudeProperties = new ClaudeProperties(
            "claude-key",
            URI.create("https://api.anthropic.com"),
            "2023-06-01",
            "claude-custom"
        );
        LlmPromptBuilder promptBuilder = new LlmPromptBuilder(llmProperties);
        ClaudeMessagesClient messagesClient = mock(ClaudeMessagesClient.class);
        when(messagesClient.createMessage(eq("claude-custom"), any(LlmPrompt.class), eq(321)))
            .thenReturn("Claude answer");
        ClaudeLlmClient client = new ClaudeLlmClient(
            llmProperties,
            claudeProperties,
            promptBuilder,
            messagesClient
        );

        String answer = client.generate(
            "질문",
            List.of(new RetrievedChunk(UUID.randomUUID(), "근거 본문", "근거 제목", "notion://page", "NOTION", 0.1)),
            List.of(new ChatContextMessage("USER", "이전 질문"))
        );

        assertThat(answer).isEqualTo("Claude answer");
        ArgumentCaptor<LlmPrompt> prompt = ArgumentCaptor.forClass(LlmPrompt.class);
        verify(messagesClient).createMessage(eq("claude-custom"), prompt.capture(), eq(321));
        assertThat(prompt.getValue().input())
            .contains("이전 대화:")
            .contains("현재 질문:\n질문")
            .contains("근거 1: 근거 제목");
    }

    @Test
    void returnsNoEvidenceMessageWithoutCallingClaudeWhenChunksAreEmpty() {
        LlmProperties llmProperties = new LlmProperties(
            "claude",
            5,
            1200,
            321,
            Duration.ofSeconds(5),
            Duration.ofSeconds(30)
        );
        ClaudeMessagesClient messagesClient = mock(ClaudeMessagesClient.class);
        ClaudeLlmClient client = new ClaudeLlmClient(
            llmProperties,
            new ClaudeProperties(null, null, null, null),
            new LlmPromptBuilder(llmProperties),
            messagesClient
        );

        String answer = client.generate("질문", List.of(), List.of());

        assertThat(answer).isEqualTo("답변할 수 있는 검색 근거를 찾지 못했습니다.");
        verify(messagesClient, never()).createMessage(any(), any(), anyInt());
    }
}
