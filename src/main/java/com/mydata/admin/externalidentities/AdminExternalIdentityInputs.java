package com.mydata.admin.externalidentities;

import com.mydata.identities.ExternalIdentityProvider;

public final class AdminExternalIdentityInputs {
    private AdminExternalIdentityInputs() {
    }

    public record CreateExternalIdentityInput(
        ExternalIdentityProvider provider,
        String workspaceId,
        String userId,
        String externalWorkspaceId,
        String externalUserId,
        String email,
        String displayName
    ) {
    }

    public record UpdateExternalIdentityInput(
        String workspaceId,
        String userId,
        String externalWorkspaceId,
        String externalUserId,
        String email,
        String displayName
    ) {
    }
}
