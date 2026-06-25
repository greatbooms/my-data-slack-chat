package com.mydata.security;

import com.mydata.slackbot.SlackBotProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecuritySecretValidatorTest {
    @Test
    void allowsBlankSigningSecretWhenSlackHttpEventsAreDisabled() {
        SecuritySecretValidator validator = new SecuritySecretValidator(
            new MockEnvironment(),
            new SlackBotProperties("", "", "", false, false)
        );

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    void rejectsBlankSlackSigningSecretWhenHttpEventsAreEnabled() {
        SecuritySecretValidator validator = new SecuritySecretValidator(
            new MockEnvironment(),
            new SlackBotProperties("\t", "", "", false, true)
        );

        assertThatThrownBy(validator::validate)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("my-data.slack.signing-secret 값");
    }

    @Test
    void rejectsLocalSigningSecretOutsideLocalAndTestProfilesWhenHttpEventsAreEnabled() {
        SecuritySecretValidator validator = new SecuritySecretValidator(
            new MockEnvironment(),
            new SlackBotProperties("local-signing-secret", "", "", false, true)
        );

        assertThatThrownBy(validator::validate)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("로컬 기본 보안값");
    }

    @Test
    void allowsLocalSigningSecretForLocalProfileWhenHttpEventsAreEnabled() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        SecuritySecretValidator validator = new SecuritySecretValidator(
            environment,
            new SlackBotProperties("local-signing-secret", "", "", false, true)
        );

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingSocketModeTokensWhenSocketModeIsEnabled() {
        SecuritySecretValidator validator = new SecuritySecretValidator(
            new MockEnvironment(),
            new SlackBotProperties("", "xapp-token", "", true, false)
        );

        assertThatThrownBy(validator::validate)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("my-data.slack.bot-token 값");
    }

    @Test
    void allowsSocketModeTokensWithoutSigningSecret() {
        SecuritySecretValidator validator = new SecuritySecretValidator(
            new MockEnvironment(),
            new SlackBotProperties("", "xapp-token", "xoxb-token", true, false)
        );

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }
}
