package com.mydata.auth;

import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrincipalKeysTest {
    @Test
    void createsInternalUserPrincipal() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        assertThat(PrincipalKeys.user(userId))
            .isEqualTo("USER:11111111-1111-1111-1111-111111111111");
    }

    @Test
    void createsWorkspacePrincipal() {
        UUID workspaceId = UUID.fromString("22222222-2222-2222-2222-222222222222");

        assertThat(PrincipalKeys.workspace(workspaceId))
            .isEqualTo("WORKSPACE:22222222-2222-2222-2222-222222222222");
    }

    @Test
    void createsSlackUserPrincipal() {
        assertThat(PrincipalKeys.slackUser("T123", "U456"))
            .isEqualTo("SLACK_USER:T123:U456");
    }

    @Test
    void createsSlackWorkspacePrincipal() {
        assertThat(PrincipalKeys.slackWorkspace("T123"))
            .isEqualTo("SLACK_WORKSPACE:T123");
    }

    @Test
    void createsSlackChannelPrincipal() {
        assertThat(PrincipalKeys.slackChannel("T123", "C789"))
            .isEqualTo("SLACK_CHANNEL:T123:C789");
    }

    @Test
    void createsGoogleUserPrincipal() {
        assertThat(PrincipalKeys.googleUser("Owner@Example.com"))
            .isEqualTo("GOOGLE_USER:owner@example.com");
    }

    @Test
    void createsGoogleUserPrincipalWithRootLocale() {
        Locale originalLocale = Locale.getDefault();

        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));

            assertThat(PrincipalKeys.googleUser("INFO@Example.com"))
                .isEqualTo("GOOGLE_USER:info@example.com");
        } finally {
            Locale.setDefault(originalLocale);
        }
    }

    @Test
    void rejectsNullInputs() {
        assertThatThrownBy(() -> PrincipalKeys.user(null))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PrincipalKeys.slackUser(null, "U456"))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PrincipalKeys.slackUser("T123", null))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PrincipalKeys.slackChannel("T123", null))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PrincipalKeys.googleUser(null))
            .isInstanceOf(NullPointerException.class);
    }
}
