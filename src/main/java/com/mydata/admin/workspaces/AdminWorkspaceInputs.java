package com.mydata.admin.workspaces;

public final class AdminWorkspaceInputs {
    private AdminWorkspaceInputs() {
    }

    public record CreateWorkspaceInput(
        String ownerUserId,
        String name
    ) {
    }

    public record UpdateWorkspaceInput(
        String ownerUserId,
        String name
    ) {
    }
}
