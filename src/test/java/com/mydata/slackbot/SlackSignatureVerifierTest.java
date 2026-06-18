package com.mydata.slackbot;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlackSignatureVerifierTest {
    @Test
    void validatesKnownSlackHmacSignature() {
        SlackSignatureVerifier verifier = new SlackSignatureVerifier("secret");

        boolean valid = verifier.isValid(
            "1531420618",
            "token=xyzz&team_id=T1&api_app_id=A1",
            "v0=30317748305cb9d57926e965f5cc6d3a776950ff47a8d4957f4db8efc008eea8"
        );

        assertThat(valid).isTrue();
    }

    @Test
    void rejectsInvalidSignature() {
        SlackSignatureVerifier verifier = new SlackSignatureVerifier("secret");

        boolean valid = verifier.isValid(
            "1531420618",
            "token=xyzz&team_id=T1&api_app_id=A1",
            "v0=not-the-signature"
        );

        assertThat(valid).isFalse();
    }
}
