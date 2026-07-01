package com.mydata.admin.externalidentities;

import com.mydata.admin.externalidentities.AdminExternalIdentityInputs.CreateExternalIdentityInput;
import com.mydata.admin.externalidentities.AdminExternalIdentityInputs.UpdateExternalIdentityInput;
import com.mydata.identities.ExternalIdentityEntity;
import com.mydata.identities.ExternalIdentityProvider;
import com.mydata.identities.ExternalIdentityRepository;
import com.mydata.users.UserEntity;
import com.mydata.users.UserRepository;
import com.mydata.workspaces.WorkspaceEntity;
import com.mydata.workspaces.WorkspaceRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AdminExternalIdentityService {
    private final ExternalIdentityRepository externalIdentities;
    private final WorkspaceRepository workspaces;
    private final UserRepository users;

    public AdminExternalIdentityService(
        ExternalIdentityRepository externalIdentities,
        WorkspaceRepository workspaces,
        UserRepository users
    ) {
        this.externalIdentities = externalIdentities;
        this.workspaces = workspaces;
        this.users = users;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public AdminExternalIdentityPagePayload listExternalIdentities() {
        List<AdminExternalIdentityPayload> items = externalIdentities.findAllByOrderByCreatedAtDesc().stream()
            .map(AdminExternalIdentityPayload::from)
            .toList();
        return new AdminExternalIdentityPagePayload(items, items.size());
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public AdminExternalIdentityPayload createExternalIdentity(CreateExternalIdentityInput input) {
        WorkspaceEntity workspace = activeWorkspace(input.workspaceId());
        UserEntity user = activeUser(input.userId());
        ExternalIdentityProvider provider = requireProvider(input.provider());
        String externalWorkspaceId = requireText(input.externalWorkspaceId(), "externalWorkspaceId");
        String externalUserId = requireText(input.externalUserId(), "externalUserId");
        rejectDuplicate(provider, externalWorkspaceId, externalUserId, null);

        ExternalIdentityEntity identity = ExternalIdentityEntity.create(
            provider,
            workspace.getId(),
            user.getId(),
            externalWorkspaceId,
            externalUserId,
            input.email(),
            input.displayName()
        );
        return AdminExternalIdentityPayload.from(externalIdentities.save(identity));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public AdminExternalIdentityPayload updateExternalIdentity(String id, UpdateExternalIdentityInput input) {
        ExternalIdentityEntity identity = activeExternalIdentity(id);
        WorkspaceEntity workspace = activeWorkspace(input.workspaceId());
        UserEntity user = activeUser(input.userId());
        String externalWorkspaceId = requireText(input.externalWorkspaceId(), "externalWorkspaceId");
        String externalUserId = requireText(input.externalUserId(), "externalUserId");
        rejectDuplicate(identity.getProvider(), externalWorkspaceId, externalUserId, identity.getId());

        identity.updateMapping(
            identity.getProvider(),
            workspace.getId(),
            user.getId(),
            externalWorkspaceId,
            externalUserId,
            input.email(),
            input.displayName()
        );
        return AdminExternalIdentityPayload.from(identity);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public boolean deleteExternalIdentity(String id) {
        ExternalIdentityEntity identity = activeExternalIdentity(id);
        externalIdentities.delete(identity);
        return true;
    }

    private ExternalIdentityEntity activeExternalIdentity(String id) {
        return externalIdentities.findById(parseId(id, "externalIdentityId"))
            .orElseThrow(() -> new IllegalArgumentException("외부 계정 매핑을 찾을 수 없습니다"));
    }

    private WorkspaceEntity activeWorkspace(String id) {
        return workspaces.findByIdAndDeletedAtIsNull(parseId(id, "workspaceId"))
            .orElseThrow(() -> new IllegalArgumentException("워크스페이스를 찾을 수 없습니다"));
    }

    private UserEntity activeUser(String id) {
        return users.findByIdAndDeletedAtIsNull(parseId(id, "userId"))
            .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다"));
    }

    private void rejectDuplicate(
        ExternalIdentityProvider provider,
        String externalWorkspaceId,
        String externalUserId,
        UUID currentIdentityId
    ) {
        externalIdentities.findByProviderAndExternalWorkspaceIdAndExternalUserId(
                provider,
                externalWorkspaceId,
                externalUserId
            )
            .filter(identity -> !identity.getId().equals(currentIdentityId))
            .ifPresent(identity -> {
                throw new IllegalArgumentException("이미 존재하는 외부 계정 매핑입니다");
            });
    }

    private static ExternalIdentityProvider requireProvider(ExternalIdentityProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("provider 값은 비어 있을 수 없습니다");
        }
        return provider;
    }

    private static UUID parseId(String id, String fieldName) {
        try {
            return UUID.fromString(id);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(fieldName + " 형식이 올바르지 않습니다", exception);
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 값은 비어 있을 수 없습니다");
        }
        return value.trim();
    }
}
