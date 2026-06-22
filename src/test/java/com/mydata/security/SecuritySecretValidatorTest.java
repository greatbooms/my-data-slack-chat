package com.mydata.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecuritySecretValidatorTest {
    @Test
    void rejectsBlankSlackSigningSecret() {
        SecuritySecretValidator validator = new SecuritySecretValidator(
            new MockEnvironment(),
            "\t"
        );

        assertThatThrownBy(validator::validate)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("my-data.slack.signing-secret 값");
    }

    @Test
    void rejectsLocalDefaultsOutsideLocalAndTestProfiles() {
        SecuritySecretValidator validator = new SecuritySecretValidator(
            new MockEnvironment(),
            "local-signing-secret"
        );

        assertThatThrownBy(validator::validate)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("로컬 기본 보안값");
    }

    @Test
    void allowsLocalDefaultsForLocalProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        SecuritySecretValidator validator = new SecuritySecretValidator(
            environment,
            "local-signing-secret"
        );

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }
}
