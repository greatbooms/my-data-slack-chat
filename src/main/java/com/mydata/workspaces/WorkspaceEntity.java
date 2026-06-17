package com.mydata.workspaces;

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
@Table(name = "workspaces")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkspaceEntity extends BaseEntity {
    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Column(nullable = false, columnDefinition = "text")
    private String name;

    public static WorkspaceEntity create(UUID ownerUserId, String name) {
        WorkspaceEntity workspace = new WorkspaceEntity();
        workspace.ownerUserId = ownerUserId;
        workspace.name = name;
        return workspace;
    }
}
