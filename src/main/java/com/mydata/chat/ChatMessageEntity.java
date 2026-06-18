package com.mydata.chat;

import com.mydata.common.domain.BaseEntity;
import com.mydata.common.json.JsonMaps;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnTransformer;

import java.util.UUID;

@Getter
@Entity
@Table(name = "chat_messages")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessageEntity extends BaseEntity {
    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(nullable = false, columnDefinition = "text")
    private String role;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = JsonMaps.EMPTY_OBJECT;

    public static ChatMessageEntity create(UUID sessionId, String role, String content) {
        ChatMessageEntity message = new ChatMessageEntity();
        message.sessionId = sessionId;
        message.role = role;
        message.content = content;
        message.metadataJson = JsonMaps.EMPTY_OBJECT;
        return message;
    }
}
