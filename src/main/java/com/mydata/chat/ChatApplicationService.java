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
    private static final int CONTEXT_MESSAGE_LIMIT = 6;
    private static final int CONTEXT_MESSAGE_CHAR_LIMIT = 800;
    private static final int RETRIEVAL_QUERY_CHAR_LIMIT = 3_000;

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
        if (externalThreadId == null || externalThreadId.isBlank()) {
            throw new IllegalArgumentException("externalThreadId가 필요합니다");
        }

        ChatSessionEntity session = findOrCreateSession(
            workspaceId,
            channelType,
            externalChannelId,
            externalThreadId,
            userId
        );

        List<ChatContextMessage> contextMessages = recentContextMessages(
            messages.findBySessionIdOrderByCreatedAt(session.getId())
        );
        messages.save(ChatMessageEntity.create(session.getId(), "USER", question));
        List<RetrievedChunk> chunks = retrieval.retrieve(
            workspaceId,
            principalKeys,
            retrievalQuery(question, contextMessages),
            RETRIEVAL_LIMIT
        );
        String content = llm.generate(question, chunks, contextMessages);
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

    private ChatSessionEntity findOrCreateSession(
        UUID workspaceId,
        String channelType,
        String externalChannelId,
        String externalThreadId,
        UUID userId
    ) {
        return sessions.findByChannel(workspaceId, channelType, externalChannelId, externalThreadId)
            .orElseGet(() -> {
                sessions.insertIfAbsent(
                    UUID.randomUUID(),
                    workspaceId,
                    channelType,
                    externalChannelId,
                    externalThreadId,
                    userId
                );
                return sessions.findByChannel(workspaceId, channelType, externalChannelId, externalThreadId)
                    .orElseThrow(() -> new IllegalStateException("채팅 세션을 생성하지 못했습니다"));
            });
    }

    private List<ChatContextMessage> recentContextMessages(List<ChatMessageEntity> existingMessages) {
        if (existingMessages == null || existingMessages.isEmpty()) {
            return List.of();
        }

        int fromIndex = Math.max(0, existingMessages.size() - CONTEXT_MESSAGE_LIMIT);
        return existingMessages.subList(fromIndex, existingMessages.size()).stream()
            .filter(message -> message.getContent() != null && !message.getContent().isBlank())
            .map(message -> new ChatContextMessage(
                normalizeRole(message.getRole()),
                trimToLimit(message.getContent(), CONTEXT_MESSAGE_CHAR_LIMIT)
            ))
            .toList();
    }

    private String retrievalQuery(String question, List<ChatContextMessage> contextMessages) {
        if (contextMessages == null || contextMessages.isEmpty()) {
            return question;
        }

        StringBuilder query = new StringBuilder("이전 대화:\n");
        for (ChatContextMessage contextMessage : contextMessages) {
            query.append(contextMessage.role())
                .append(": ")
                .append(contextMessage.content())
                .append("\n");
        }
        query.append("\n현재 질문:\n").append(question);
        return trimFromStart(query.toString(), RETRIEVAL_QUERY_CHAR_LIMIT);
    }

    private String normalizeRole(String role) {
        if ("ASSISTANT".equalsIgnoreCase(role)) {
            return "ASSISTANT";
        }
        if ("USER".equalsIgnoreCase(role)) {
            return "USER";
        }
        return "UNKNOWN";
    }

    private String trimToLimit(String value, int limit) {
        if (value == null || value.length() <= limit) {
            return value;
        }
        return value.substring(0, limit);
    }

    private String trimFromStart(String value, int limit) {
        if (value == null || value.length() <= limit) {
            return value;
        }
        return value.substring(value.length() - limit);
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
