package com.mydata.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Modifying
    @Query(value = """
        INSERT INTO chat_sessions (
            id,
            workspace_id,
            channel_type,
            external_channel_id,
            external_thread_id,
            created_by_user_id
        )
        VALUES (
            :id,
            :workspaceId,
            :channelType,
            :externalChannelId,
            :externalThreadId,
            :createdByUserId
        )
        ON CONFLICT (
            workspace_id,
            channel_type,
            COALESCE(external_channel_id, ''),
            COALESCE(external_thread_id, '')
        ) DO NOTHING
        """, nativeQuery = true)
    int insertIfAbsent(
        @Param("id") UUID id,
        @Param("workspaceId") UUID workspaceId,
        @Param("channelType") String channelType,
        @Param("externalChannelId") String externalChannelId,
        @Param("externalThreadId") String externalThreadId,
        @Param("createdByUserId") UUID createdByUserId
    );
}
