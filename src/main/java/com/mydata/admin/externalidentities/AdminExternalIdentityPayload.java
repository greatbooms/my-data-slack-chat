package com.mydata.admin.externalidentities;

import com.mydata.identities.ExternalIdentityEntity;
import com.mydata.identities.ExternalIdentityProvider;

import java.util.UUID;

public record AdminExternalIdentityPayload(
    UUID id,
    UUID workspaceId,
    UUID userId,
    ExternalIdentityProvider provider,
    String externalWorkspaceId,
    String externalUserId,
    String email,
    String displayName,
    String principalKey
) {
    public static AdminExternalIdentityPayload from(ExternalIdentityEntity identity) {
        return new AdminExternalIdentityPayload(
            identity.getId(),
            identity.getWorkspaceId(),
            identity.getUserId(),
            identity.getProvider(),
            identity.getExternalWorkspaceId(),
            identity.getExternalUserId(),
            identity.getEmail(),
            identity.getDisplayName(),
            identity.getPrincipalKey()
        );
    }
}
