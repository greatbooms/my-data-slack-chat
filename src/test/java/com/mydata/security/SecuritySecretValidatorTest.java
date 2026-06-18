package com.mydata.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecuritySecretValidatorTest {
    @Test
    void rejectsBlankAdminToken() {
        SecuritySecretValidator validator = new SecuritySecretValidator(
            new MockEnvironment(),
            " ",
            "slack-secret"
        );

        assertThatThrownBy(validator::validate)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("my-data.admin-token");
    }

    @Test
    void rejectsBlankSlackSigningSecret() {
        SecuritySecretValidator validator = new SecuritySecretValidator(
            new MockEnvironment(),
            "admin-secret",
            "\t"
        );

        assertThatThrownBy(validator::validate)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("my-data.slack.signing-secret");
    }

    @Test
    void rejectsLocalDefaultsOutsideLocalAndTestProfiles() {
        SecuritySecretValidator validator = new SecuritySecretValidator(
            new MockEnvironment(),
            "local-admin-token",
            "local-signing-secret"
        );

        assertThatThrownBy(validator::validate)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("local defaults");
    }

    @Test
    void allowsLocalDefaultsForLocalProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        SecuritySecretValidator validator = new SecuritySecretValidator(
            environment,
            "local-admin-token",
            "local-signing-secret"
        );

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }
}
