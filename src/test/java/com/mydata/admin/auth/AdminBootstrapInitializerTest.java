package com.mydata.admin.auth;

import com.mydata.support.PostgresIntegrationTest;
import com.mydata.users.UserEntity;
import com.mydata.users.UserRepository;
import com.mydata.users.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class AdminBootstrapInitializerTest extends PostgresIntegrationTest {
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void createsBootstrapAdminWhenNoNonDeletedAdminExists() throws Exception {
        AdminBootstrapInitializer initializer = initializer(
            new AdminBootstrapProperties("admin@example.com", "secret1234", "관리자"),
            new MockEnvironment()
        );

        initializer.run(new DefaultApplicationArguments());

        UserEntity admin = users.findByEmailAndDeletedAtIsNull("admin@example.com").orElseThrow();
        assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(admin.getDisplayName()).isEqualTo("관리자");
        assertThat(passwordEncoder.matches("secret1234", admin.getPasswordHash())).isTrue();
    }

    @Test
    void doesNotCreateBootstrapAdminWhenNonDeletedAdminAlreadyExists() throws Exception {
        UserEntity existingAdmin = UserEntity.create("existing-admin@example.com", "Existing");
        existingAdmin.changeRole(UserRole.ADMIN);
        existingAdmin.updatePasswordHash(passwordEncoder.encode("original-password"));
        users.save(existingAdmin);

        AdminBootstrapInitializer initializer = initializer(
            new AdminBootstrapProperties("admin@example.com", "secret1234", "관리자"),
            new MockEnvironment()
        );

        initializer.run(new DefaultApplicationArguments());

        assertThat(users.findByEmailAndDeletedAtIsNull("admin@example.com")).isEmpty();
        assertThat(nonDeletedAdminCount()).isEqualTo(1);
    }

    @Test
    void restoresDeletedBootstrapUserWhenNoNonDeletedAdminExists() throws Exception {
        UserEntity deletedAdmin = UserEntity.create("admin@example.com", "Old");
        deletedAdmin.changeRole(UserRole.ADMIN);
        deletedAdmin.markDeleted();
        users.save(deletedAdmin);

        AdminBootstrapInitializer initializer = initializer(
            new AdminBootstrapProperties("admin@example.com", "secret1234", "관리자"),
            new MockEnvironment()
        );

        initializer.run(new DefaultApplicationArguments());

        UserEntity admin = users.findByEmailAndDeletedAtIsNull("admin@example.com").orElseThrow();
        assertThat(admin.getId()).isEqualTo(deletedAdmin.getId());
        assertThat(admin.getDisplayName()).isEqualTo("관리자");
        assertThat(passwordEncoder.matches("secret1234", admin.getPasswordHash())).isTrue();
    }

    @Test
    void failsOutsideTestProfileWhenBootstrapCredentialsAreMissing() {
        AdminBootstrapInitializer initializer = initializer(
            new AdminBootstrapProperties("", "", "관리자"),
            new MockEnvironment()
        );

        assertThatThrownBy(() -> initializer.run(new DefaultApplicationArguments()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("초기 관리자");
    }

    @Test
    void skipsMissingBootstrapCredentialsInTestProfile() throws Exception {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");
        AdminBootstrapInitializer initializer = initializer(
            new AdminBootstrapProperties("", "", "관리자"),
            environment
        );

        initializer.run(new DefaultApplicationArguments());

        assertThat(nonDeletedAdminCount()).isZero();
    }

    private AdminBootstrapInitializer initializer(AdminBootstrapProperties properties, MockEnvironment environment) {
        return new AdminBootstrapInitializer(users, passwordEncoder, properties, environment);
    }

    private long nonDeletedAdminCount() {
        return users.findByDeletedAtIsNullOrderByCreatedAtDesc().stream()
            .filter(user -> user.getRole() == UserRole.ADMIN)
            .count();
    }
}
