package com.mydata.admin.auth;

import com.mydata.users.UserEntity;
import com.mydata.users.UserRepository;
import com.mydata.users.UserRole;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminBootstrapInitializer implements ApplicationRunner {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AdminBootstrapProperties properties;
    private final Environment environment;

    public AdminBootstrapInitializer(
        UserRepository users,
        PasswordEncoder passwordEncoder,
        AdminBootstrapProperties properties,
        Environment environment
    ) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (hasNonDeletedAdmin()) {
            return;
        }

        String email = trimToEmpty(properties.email());
        String password = trimToEmpty(properties.password());
        if (email.isBlank() || password.isBlank()) {
            if (environment.acceptsProfiles(Profiles.of("test"))) {
                return;
            }
            throw new IllegalStateException("초기 관리자 이메일과 비밀번호를 설정해야 합니다");
        }

        UserEntity admin = users.findByEmail(email)
            .map(existingUser -> {
                existingUser.restore();
                return existingUser;
            })
            .orElseGet(() -> UserEntity.create(email, displayName()));
        admin.updateProfile(displayName());
        admin.changeRole(UserRole.ADMIN);
        admin.updatePasswordHash(passwordEncoder.encode(password));
        users.save(admin);
    }

    private boolean hasNonDeletedAdmin() {
        return users.findByDeletedAtIsNullOrderByCreatedAtDesc().stream()
            .anyMatch(user -> user.getRole() == UserRole.ADMIN);
    }

    private String displayName() {
        String displayName = trimToEmpty(properties.displayName());
        if (displayName.isBlank()) {
            return "관리자";
        }
        return displayName;
    }

    private static String trimToEmpty(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }
}
