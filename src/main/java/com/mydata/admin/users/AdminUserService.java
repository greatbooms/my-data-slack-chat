package com.mydata.admin.users;

import com.mydata.admin.users.AdminUserInputs.CreateUserInput;
import com.mydata.admin.users.AdminUserInputs.ResetUserPasswordInput;
import com.mydata.admin.users.AdminUserInputs.UpdateUserInput;
import com.mydata.admin.workspaces.AdminWorkspaceService;
import com.mydata.users.UserEntity;
import com.mydata.users.UserRepository;
import com.mydata.users.UserRole;
import com.mydata.users.UserStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AdminUserService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AdminWorkspaceService adminWorkspaces;

    public AdminUserService(
        UserRepository users,
        PasswordEncoder passwordEncoder,
        AdminWorkspaceService adminWorkspaces
    ) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.adminWorkspaces = adminWorkspaces;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public AdminUserPagePayload listUsers() {
        List<AdminUserPayload> items = users.findByDeletedAtIsNullOrderByCreatedAtDesc().stream()
            .map(AdminUserPayload::from)
            .toList();
        return new AdminUserPagePayload(items, items.size());
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public AdminUserPayload findUser(String id) {
        return AdminUserPayload.from(activeUser(id));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public AdminUserPayload createUser(CreateUserInput input) {
        String email = requireText(input.email(), "email");
        if (users.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("이미 존재하는 이메일입니다");
        }

        UserEntity user = UserEntity.create(email, requireText(input.displayName(), "displayName"));
        user.changeRole(input.role() == null ? UserRole.USER : input.role());
        user.changeStatus(UserStatus.ACTIVE);
        user.updatePasswordHash(passwordEncoder.encode(requireText(input.password(), "password")));
        UserEntity savedUser = users.save(user);
        adminWorkspaces.ensureDefaultWorkspace(savedUser);
        return AdminUserPayload.from(savedUser);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public AdminUserPayload updateUser(String id, UpdateUserInput input) {
        UserEntity user = activeUser(id);
        if (hasText(input.displayName())) {
            user.updateProfile(input.displayName().trim());
        }
        if (input.role() != null) {
            user.changeRole(input.role());
        }
        if (input.status() != null) {
            user.changeStatus(input.status());
        }
        return AdminUserPayload.from(user);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public AdminUserPayload disableUser(String id) {
        UserEntity user = activeUser(id);
        user.changeStatus(UserStatus.DISABLED);
        return AdminUserPayload.from(user);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public AdminUserPayload softDeleteUser(String id) {
        UserEntity user = activeUser(id);
        user.markDeleted();
        return AdminUserPayload.from(user);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public AdminUserPayload restoreUser(String id) {
        UserEntity user = users.findById(parseId(id))
            .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다"));
        user.restore();
        return AdminUserPayload.from(user);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public AdminUserPayload resetUserPassword(String id, ResetUserPasswordInput input) {
        UserEntity user = activeUser(id);
        user.updatePasswordHash(passwordEncoder.encode(requireText(input.password(), "password")));
        return AdminUserPayload.from(user);
    }

    private UserEntity activeUser(String id) {
        return users.findByIdAndDeletedAtIsNull(parseId(id))
            .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다"));
    }

    private static UUID parseId(String id) {
        try {
            return UUID.fromString(id);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("사용자 ID 형식이 올바르지 않습니다", exception);
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
