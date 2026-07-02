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

class OpenAiLlmClientTest {
    @Test
    void usesOpenAiSpecificModelWhenGeneratingAnswer() {
        LlmProperties llmProperties = new LlmProperties(
            "openai",
            5,
            1200,
            321,
            Duration.ofSeconds(5),
            Duration.ofSeconds(30)
        );
        OpenAiProperties openAiProperties = new OpenAiProperties(
            "openai-key",
            URI.create("https://api.openai.com"),
            "openai-custom"
        );
        LlmPromptBuilder promptBuilder = new LlmPromptBuilder(llmProperties);
        OpenAiResponsesClient responsesClient = mock(OpenAiResponsesClient.class);
        when(responsesClient.createResponse(eq("openai-custom"), any(LlmPrompt.class), eq(321)))
            .thenReturn("OpenAI answer");
        OpenAiLlmClient client = new OpenAiLlmClient(
            llmProperties,
            openAiProperties,
            promptBuilder,
            responsesClient
        );

        String answer = client.generate(
            "질문",
            List.of(new RetrievedChunk(UUID.randomUUID(), "근거 본문", "근거 제목", "notion://page", "NOTION", 0.1)),
            List.of(new ChatContextMessage("USER", "이전 질문"))
        );

        assertThat(answer).isEqualTo("OpenAI answer");
        ArgumentCaptor<LlmPrompt> prompt = ArgumentCaptor.forClass(LlmPrompt.class);
        verify(responsesClient).createResponse(eq("openai-custom"), prompt.capture(), eq(321));
        assertThat(prompt.getValue().input())
            .contains("이전 대화:")
            .contains("현재 질문:\n질문")
            .contains("근거 1: 근거 제목");
    }

    @Test
    void returnsNoEvidenceMessageWithoutCallingOpenAiWhenChunksAreEmpty() {
        LlmProperties llmProperties = new LlmProperties(
            "openai",
            5,
            1200,
            321,
            Duration.ofSeconds(5),
            Duration.ofSeconds(30)
        );
        OpenAiResponsesClient responsesClient = mock(OpenAiResponsesClient.class);
        OpenAiLlmClient client = new OpenAiLlmClient(
            llmProperties,
            new OpenAiProperties(null, null, null),
            new LlmPromptBuilder(llmProperties),
            responsesClient
        );

        String answer = client.generate("질문", List.of(), List.of());

        assertThat(answer).isEqualTo("답변할 수 있는 검색 근거를 찾지 못했습니다.");
        verify(responsesClient, never()).createResponse(any(), any(), anyInt());
    }
}
