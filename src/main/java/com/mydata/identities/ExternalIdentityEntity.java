package com.mydata.identities;

import com.mydata.auth.PrincipalKeys;
import com.mydata.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Entity
@Table(name = "external_identities")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExternalIdentityEntity extends BaseEntity {
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "text")
    private ExternalIdentityProvider provider;

    @Column(name = "external_workspace_id", nullable = false, columnDefinition = "text")
    private String externalWorkspaceId;

    @Column(name = "external_user_id", nullable = false, columnDefinition = "text")
    private String externalUserId;

    @Column(columnDefinition = "text")
    private String email;

    @Column(name = "display_name", columnDefinition = "text")
    private String displayName;

    @Column(name = "principal_key", nullable = false, columnDefinition = "text")
    private String principalKey;

    public static ExternalIdentityEntity create(
        ExternalIdentityProvider provider,
        UUID workspaceId,
        UUID userId,
        String externalWorkspaceId,
        String externalUserId,
        String email,
        String displayName
    ) {
        ExternalIdentityEntity identity = new ExternalIdentityEntity();
        identity.updateMapping(provider, workspaceId, userId, externalWorkspaceId, externalUserId, email, displayName);
        return identity;
    }

    public void updateMapping(
        ExternalIdentityProvider provider,
        UUID workspaceId,
        UUID userId,
        String externalWorkspaceId,
        String externalUserId,
        String email,
        String displayName
    ) {
        this.provider = requireProvider(provider);
        this.workspaceId = requireId(workspaceId, "workspaceId");
        this.userId = requireId(userId, "userId");
        this.externalWorkspaceId = requireText(externalWorkspaceId, "externalWorkspaceId");
        this.externalUserId = requireText(externalUserId, "externalUserId");
        this.email = optionalText(email);
        this.displayName = optionalText(displayName);
        this.principalKey = principalKey(this.provider, this.externalWorkspaceId, this.externalUserId);
    }

    private static String principalKey(
        ExternalIdentityProvider provider,
        String externalWorkspaceId,
        String externalUserId
    ) {
        return switch (provider) {
            case SLACK -> PrincipalKeys.slackUser(externalWorkspaceId, externalUserId);
        };
    }

    private static ExternalIdentityProvider requireProvider(ExternalIdentityProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("provider 값은 비어 있을 수 없습니다");
        }
        return provider;
    }

    private static UUID requireId(UUID value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " 값은 비어 있을 수 없습니다");
        }
        return value;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 값은 비어 있을 수 없습니다");
        }
        return value.trim();
    }

    private static String optionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
