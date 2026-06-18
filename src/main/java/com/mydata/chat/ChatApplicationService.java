package com.mydata.chat;

import com.mydata.retrieval.RetrievalService;
import com.mydata.retrieval.RetrievedChunk;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ChatApplicationService {
    private static final int RETRIEVAL_LIMIT = 5;

    private final ChatSessionRepository sessions;
    private final ChatMessageRepository messages;
    private final ChatRetrievalCitationRepository citations;
    private final RetrievalService retrieval;
    private final LlmClient llm;

    public ChatApplicationService(
        ChatSessionRepository sessions,
        ChatMessageRepository messages,
        ChatRetrievalCitationRepository citations,
        RetrievalService retrieval,
        LlmClient llm
    ) {
        this.sessions = sessions;
        this.messages = messages;
        this.citations = citations;
        this.retrieval = retrieval;
        this.llm = llm;
    }

    @Transactional
    public Answer answer(
        UUID workspaceId,
        UUID userId,
        String channelType,
        String externalChannelId,
        String externalThreadId,
        List<String> principalKeys,
        String question
    ) {
        ChatSessionEntity session = sessions.findByChannel(
            workspaceId,
            channelType,
            externalChannelId,
            externalThreadId
        ).orElseGet(() -> sessions.save(ChatSessionEntity.create(
            workspaceId,
            channelType,
            externalChannelId,
            externalThreadId,
            userId
        )));

        messages.save(ChatMessageEntity.create(session.getId(), "USER", question));
        List<RetrievedChunk> chunks = retrieval.retrieve(workspaceId, principalKeys, question, RETRIEVAL_LIMIT);
        String content = llm.generate(question, chunks);
        ChatMessageEntity assistantMessage = messages.save(ChatMessageEntity.create(
            session.getId(),
            "ASSISTANT",
            content
        ));

        List<Citation> answerCitations = new ArrayList<>();
        for (int index = 0; index < chunks.size(); index++) {
            RetrievedChunk chunk = chunks.get(index);
            int rank = index + 1;
            citations.save(ChatRetrievalCitationEntity.create(
                assistantMessage.getId(),
                chunk.chunkId(),
                rank,
                chunk.distance()
            ));
            answerCitations.add(new Citation(
                chunk.chunkId(),
                chunk.title(),
                chunk.uri(),
                chunk.sourceType(),
                rank,
                chunk.distance()
            ));
        }

        return new Answer(session.getId(), content, List.copyOf(answerCitations));
    }

    public record Answer(UUID sessionId, String content, List<Citation> citations) {
    }

    public record Citation(
        UUID chunkId,
        String title,
        String uri,
        String sourceType,
        int rank,
        Double score
    ) {
    }
}
