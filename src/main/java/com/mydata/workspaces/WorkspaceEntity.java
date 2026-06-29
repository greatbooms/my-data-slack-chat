package com.mydata.workspaces;

import com.mydata.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(name = "workspaces")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkspaceEntity extends BaseEntity {
    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Column(nullable = false, columnDefinition = "text")
    private String name;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public static WorkspaceEntity create(UUID ownerUserId, String name) {
        WorkspaceEntity workspace = new WorkspaceEntity();
        workspace.ownerUserId = ownerUserId;
        workspace.name = name;
        workspace.updatedAt = OffsetDateTime.now();
        return workspace;
    }

    public void rename(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("워크스페이스 이름은 비어 있을 수 없습니다");
        }
        this.name = name.trim();
        touch();
    }

    public void changeOwner(UUID ownerUserId) {
        if (ownerUserId == null) {
            throw new IllegalArgumentException("소유 유저는 비어 있을 수 없습니다");
        }
        this.ownerUserId = ownerUserId;
        touch();
    }

    public void markDeleted() {
        deletedAt = OffsetDateTime.now();
        touch();
    }

    public void restore() {
        deletedAt = null;
        touch();
    }

    private void touch() {
        updatedAt = OffsetDateTime.now();
    }
}
