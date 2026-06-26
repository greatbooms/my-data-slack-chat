package com.mydata.admin.workspaces;

import com.mydata.admin.workspaces.AdminWorkspaceInputs.CreateWorkspaceInput;
import com.mydata.admin.workspaces.AdminWorkspaceInputs.UpdateWorkspaceInput;
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
public class AdminWorkspaceService {
    private static final String DEFAULT_WORKSPACE_NAME = "Personal";

    private final WorkspaceRepository workspaces;
    private final UserRepository users;

    public AdminWorkspaceService(WorkspaceRepository workspaces, UserRepository users) {
        this.workspaces = workspaces;
        this.users = users;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public AdminWorkspacePagePayload listWorkspaces(Boolean includeDeleted) {
        List<WorkspaceEntity> workspaceItems = Boolean.TRUE.equals(includeDeleted)
            ? workspaces.findAll()
            : workspaces.findByDeletedAtIsNullOrderByCreatedAtDesc();
        List<AdminWorkspacePayload> items = workspaceItems.stream()
            .map(AdminWorkspacePayload::from)
            .toList();
        return new AdminWorkspacePagePayload(items, items.size());
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public AdminWorkspacePayload createWorkspace(CreateWorkspaceInput input) {
        WorkspaceEntity workspace = WorkspaceEntity.create(
            requireActiveUser(input.ownerUserId()).getId(),
            requireText(input.name(), "name")
        );
        return AdminWorkspacePayload.from(workspaces.save(workspace));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public AdminWorkspacePayload updateWorkspace(String id, UpdateWorkspaceInput input) {
        WorkspaceEntity workspace = activeWorkspace(id);
        if (hasText(input.name())) {
            workspace.rename(input.name());
        }
        if (hasText(input.ownerUserId())) {
            workspace.changeOwner(requireActiveUser(input.ownerUserId()).getId());
        }
        return AdminWorkspacePayload.from(workspace);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public AdminWorkspacePayload softDeleteWorkspace(String id) {
        WorkspaceEntity workspace = activeWorkspace(id);
        workspace.markDeleted();
        return AdminWorkspacePayload.from(workspace);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public AdminWorkspacePayload restoreWorkspace(String id) {
        WorkspaceEntity workspace = workspaces.findById(parseId(id, "workspaceId"))
            .orElseThrow(() -> new IllegalArgumentException("워크스페이스를 찾을 수 없습니다"));
        workspace.restore();
        return AdminWorkspacePayload.from(workspace);
    }

    @Transactional
    public void ensureDefaultWorkspace(UserEntity user) {
        if (user == null || user.getDeletedAt() != null) {
            return;
        }
        if (!workspaces.findByOwnerUserIdAndDeletedAtIsNull(user.getId()).isEmpty()) {
            return;
        }

        workspaces.saveAndFlush(WorkspaceEntity.create(user.getId(), DEFAULT_WORKSPACE_NAME));
    }

    @Transactional
    public void ensureDefaultWorkspacesForActiveUsers() {
        users.findByDeletedAtIsNullOrderByCreatedAtDesc()
            .forEach(this::ensureDefaultWorkspace);
    }

    private WorkspaceEntity activeWorkspace(String id) {
        return workspaces.findByIdAndDeletedAtIsNull(parseId(id, "workspaceId"))
            .orElseThrow(() -> new IllegalArgumentException("워크스페이스를 찾을 수 없습니다"));
    }

    private UserEntity requireActiveUser(String id) {
        return users.findByIdAndDeletedAtIsNull(parseId(id, "ownerUserId"))
            .orElseThrow(() -> new IllegalArgumentException("소유자를 찾을 수 없습니다"));
    }

    private static UUID parseId(String id, String fieldName) {
        try {
            return UUID.fromString(id);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(fieldName + " 형식이 올바르지 않습니다", exception);
        }
    }

    private static String requireText(String value, String fieldName) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(fieldName + " 값은 비어 있을 수 없습니다");
        }
        return value.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
