package com.mydata.auth;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PrincipalKeysTest {
    @Test
    void createsInternalUserPrincipal() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        assertThat(PrincipalKeys.user(userId))
            .isEqualTo("USER:11111111-1111-1111-1111-111111111111");
    }

    @Test
    void createsSlackUserPrincipal() {
        assertThat(PrincipalKeys.slackUser("T123", "U456"))
            .isEqualTo("SLACK_USER:T123:U456");
    }

    @Test
    void createsSlackChannelPrincipal() {
        assertThat(PrincipalKeys.slackChannel("C789"))
            .isEqualTo("SLACK_CHANNEL:C789");
    }

    @Test
    void createsGoogleUserPrincipal() {
        assertThat(PrincipalKeys.googleUser("Owner@Example.com"))
            .isEqualTo("GOOGLE_USER:owner@example.com");
    }
}
