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
@Table(name = "chat_sessions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatSessionEntity extends BaseEntity {
    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "channel_type", nullable = false, columnDefinition = "text")
    private String channelType;

    @Column(name = "external_channel_id", columnDefinition = "text")
    private String externalChannelId;

    @Column(name = "external_thread_id", columnDefinition = "text")
    private String externalThreadId;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    public static ChatSessionEntity create(
        UUID workspaceId,
        String channelType,
        String externalChannelId,
        String externalThreadId,
        UUID createdByUserId
    ) {
        ChatSessionEntity session = new ChatSessionEntity();
        session.workspaceId = workspaceId;
        session.channelType = channelType;
        session.externalChannelId = externalChannelId;
        session.externalThreadId = externalThreadId;
        session.createdByUserId = createdByUserId;
        return session;
    }
}
