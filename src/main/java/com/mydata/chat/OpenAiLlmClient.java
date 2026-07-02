package com.mydata.chat;

import com.mydata.retrieval.RetrievedChunk;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "my-data.llm", name = "provider", havingValue = "openai")
public class OpenAiLlmClient implements LlmClient {
    private static final String NO_EVIDENCE_MESSAGE = "답변할 수 있는 검색 근거를 찾지 못했습니다.";

    private final LlmProperties properties;
    private final LlmPromptBuilder promptBuilder;
    private final OpenAiResponsesClient responsesClient;

    public OpenAiLlmClient(
        LlmProperties properties,
        LlmPromptBuilder promptBuilder,
        OpenAiResponsesClient responsesClient
    ) {
        this.properties = properties;
        this.promptBuilder = promptBuilder;
        this.responsesClient = responsesClient;
    }

    @Override
    public String generate(String question, List<RetrievedChunk> chunks, List<ChatContextMessage> contextMessages) {
        if (chunks == null || chunks.isEmpty()) {
            return NO_EVIDENCE_MESSAGE;
        }
        return responsesClient.createResponse(
            properties.model(),
            promptBuilder.build(question, chunks, contextMessages),
            properties.maxOutputTokens()
        );
    }
}
