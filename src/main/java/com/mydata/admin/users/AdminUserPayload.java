package com.mydata.admin.users;

import com.mydata.users.UserEntity;
import com.mydata.users.UserRole;
import com.mydata.users.UserStatus;

import java.util.UUID;

public record AdminUserPayload(
    UUID id,
    String email,
    String displayName,
    UserRole role,
    UserStatus status,
    String deletedAt
) {
    public static AdminUserPayload from(UserEntity user) {
        String deletedAt = user.getDeletedAt() == null ? null : user.getDeletedAt().toString();
        return new AdminUserPayload(
            user.getId(),
            user.getEmail(),
            user.getDisplayName(),
            user.getRole(),
            user.getStatus(),
            deletedAt
        );
    }
}
