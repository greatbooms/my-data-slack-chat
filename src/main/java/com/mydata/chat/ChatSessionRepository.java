package com.mydata.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface ChatSessionRepository extends JpaRepository<ChatSessionEntity, UUID> {
    @Query("""
        SELECT session
        FROM ChatSessionEntity session
        WHERE session.workspaceId = :workspaceId
          AND session.channelType = :channelType
          AND (
            (:externalChannelId IS NULL AND session.externalChannelId IS NULL)
            OR session.externalChannelId = :externalChannelId
          )
          AND (
            (:externalThreadId IS NULL AND session.externalThreadId IS NULL)
            OR session.externalThreadId = :externalThreadId
          )
        """)
    Optional<ChatSessionEntity> findByChannel(
        UUID workspaceId,
        String channelType,
        String externalChannelId,
        String externalThreadId
    );
}
