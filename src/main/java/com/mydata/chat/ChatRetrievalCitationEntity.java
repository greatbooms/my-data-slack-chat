package com.mydata.chat;

import com.mydata.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Entity
@Table(name = "chat_retrieval_citations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRetrievalCitationEntity extends BaseEntity {
    @Column(name = "chat_message_id", nullable = false)
    private UUID chatMessageId;

    @Column(name = "chunk_id", nullable = false)
    private UUID chunkId;

    @Column(name = "rank", nullable = false)
    private int rank;

    @Column
    private Double score;

    public static ChatRetrievalCitationEntity create(UUID chatMessageId, UUID chunkId, int rank, Double score) {
        ChatRetrievalCitationEntity citation = new ChatRetrievalCitationEntity();
        citation.chatMessageId = chatMessageId;
        citation.chunkId = chunkId;
        citation.rank = rank;
        citation.score = score;
        return citation;
    }
}
