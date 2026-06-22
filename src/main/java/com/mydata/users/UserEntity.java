package com.mydata.users;

import com.mydata.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Objects;

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserEntity extends BaseEntity {
    @Column(nullable = false, unique = true, columnDefinition = "text")
    private String email;

    @Column(name = "display_name", nullable = false, columnDefinition = "text")
    private String displayName;

    @Column(name = "password_hash", columnDefinition = "text")
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "text")
    private UserRole role = UserRole.USER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "text")
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public static UserEntity create(String email, String displayName) {
        UserEntity user = new UserEntity();
        user.email = email;
        user.displayName = displayName;
        user.role = UserRole.USER;
        user.status = UserStatus.ACTIVE;
        user.updatedAt = OffsetDateTime.now();
        return user;
    }

    public void updateProfile(String displayName) {
        this.displayName = displayName;
        touch();
    }

    public void updatePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
        touch();
    }

    public void changeRole(UserRole role) {
        this.role = Objects.requireNonNull(role, "role must not be null");
        touch();
    }

    public void changeStatus(UserStatus status) {
        this.status = Objects.requireNonNull(status, "status must not be null");
        touch();
    }

    public void markDeleted() {
        this.deletedAt = OffsetDateTime.now();
        touch();
    }

    private void touch() {
        updatedAt = OffsetDateTime.now();
    }
}
