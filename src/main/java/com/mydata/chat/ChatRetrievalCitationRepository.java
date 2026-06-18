package com.mydata.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChatRetrievalCitationRepository extends JpaRepository<ChatRetrievalCitationEntity, UUID> {
    List<ChatRetrievalCitationEntity> findByChatMessageIdOrderByRank(UUID chatMessageId);
}
