package com.mydata.slackbot;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SlackSignatureVerifierTest {
    @Test
    void rejectsBlankConfiguredSigningSecret() {
        assertThatThrownBy(() -> new SlackSignatureVerifier(" "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("signing secret");
    }

    @Test
    void validatesKnownSlackHmacSignature() {
        SlackSignatureVerifier verifier = new SlackSignatureVerifier(
            "secret",
            Clock.fixed(Instant.ofEpochSecond(1531420618), ZoneOffset.UTC)
        );

        boolean valid = verifier.isValid(
            "1531420618",
            "token=xyzz&team_id=T1&api_app_id=A1",
            "v0=30317748305cb9d57926e965f5cc6d3a776950ff47a8d4957f4db8efc008eea8"
        );

        assertThat(valid).isTrue();
    }

    @Test
    void rejectsInvalidSignature() {
        SlackSignatureVerifier verifier = new SlackSignatureVerifier(
            "secret",
            Clock.fixed(Instant.ofEpochSecond(1531420618), ZoneOffset.UTC)
        );

        boolean valid = verifier.isValid(
            "1531420618",
            "token=xyzz&team_id=T1&api_app_id=A1",
            "v0=not-the-signature"
        );

        assertThat(valid).isFalse();
    }

    @Test
    void acceptsCorrectSignatureWithinFiveMinuteReplayWindow() throws Exception {
        SlackSignatureVerifier verifier = new SlackSignatureVerifier(
            "secret",
            Clock.fixed(Instant.ofEpochSecond(1_000), ZoneOffset.UTC)
        );
        String timestamp = "800";
        String body = "{\"type\":\"event_callback\"}";

        boolean valid = verifier.isValid(timestamp, body, signatureFor("secret", timestamp, body));

        assertThat(valid).isTrue();
    }

    @Test
    void rejectsCorrectSignatureOlderThanFiveMinuteReplayWindow() throws Exception {
        SlackSignatureVerifier verifier = new SlackSignatureVerifier(
            "secret",
            Clock.fixed(Instant.ofEpochSecond(1_000), ZoneOffset.UTC)
        );
        String timestamp = "699";
        String body = "{\"type\":\"event_callback\"}";

        boolean valid = verifier.isValid(timestamp, body, signatureFor("secret", timestamp, body));

        assertThat(valid).isFalse();
    }

    @Test
    void rejectsMalformedTimestamp() throws Exception {
        SlackSignatureVerifier verifier = new SlackSignatureVerifier(
            "secret",
            Clock.fixed(Instant.ofEpochSecond(1_000), ZoneOffset.UTC)
        );
        String timestamp = "not-a-number";
        String body = "{\"type\":\"event_callback\"}";

        boolean valid = verifier.isValid(timestamp, body, signatureFor("secret", timestamp, body));

        assertThat(valid).isFalse();
    }

    private static String signatureFor(String signingSecret, String timestamp, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal(("v0:" + timestamp + ":" + body).getBytes(StandardCharsets.UTF_8));
        return "v0=" + HexFormat.of().formatHex(digest);
    }
}
