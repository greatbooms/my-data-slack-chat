package com.mydata.slackbot;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;

public class SlackSignatureVerifier {
    private static final long MAX_REQUEST_AGE_SECONDS = 300;

    private final String signingSecret;
    private final Clock clock;

    public SlackSignatureVerifier(String signingSecret) {
        this(signingSecret, Clock.systemUTC());
    }

    SlackSignatureVerifier(String signingSecret, Clock clock) {
        if (signingSecret == null || signingSecret.isBlank()) {
            throw new IllegalArgumentException("signing secret must not be blank");
        }
        this.signingSecret = signingSecret;
        this.clock = clock;
    }

    public boolean isValid(String timestamp, String body, String signature) {
        if (signingSecret == null || timestamp == null || body == null || signature == null) {
            return false;
        }
        if (!signature.startsWith("v0=")) {
            return false;
        }
        if (!hasFreshTimestamp(timestamp)) {
            return false;
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(("v0:" + timestamp + ":" + body).getBytes(StandardCharsets.UTF_8));
            String expectedSignature = "v0=" + HexFormat.of().formatHex(digest);

            return MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            return false;
        }
    }

    private boolean hasFreshTimestamp(String timestamp) {
        try {
            long requestEpochSeconds = Long.parseLong(timestamp);
            long ageSeconds = Math.abs(clock.instant().getEpochSecond() - requestEpochSeconds);
            return ageSeconds <= MAX_REQUEST_AGE_SECONDS;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
