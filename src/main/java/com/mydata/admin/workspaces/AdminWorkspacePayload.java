package com.mydata.admin.workspaces;

import com.mydata.workspaces.WorkspaceEntity;

import java.util.UUID;

public record AdminWorkspacePayload(
    UUID id,
    UUID ownerUserId,
    String name,
    String deletedAt
) {
    public static AdminWorkspacePayload from(WorkspaceEntity workspace) {
        String deletedAt = workspace.getDeletedAt() == null ? null : workspace.getDeletedAt().toString();
        return new AdminWorkspacePayload(
            workspace.getId(),
            workspace.getOwnerUserId(),
            workspace.getName(),
            deletedAt
        );
    }
}
