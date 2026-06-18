package com.mydata.slackbot;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public class SlackSignatureVerifier {
    private final String signingSecret;

    public SlackSignatureVerifier(String signingSecret) {
        this.signingSecret = signingSecret;
    }

    public boolean isValid(String timestamp, String body, String signature) {
        if (signingSecret == null || timestamp == null || body == null || signature == null) {
            return false;
        }
        if (!signature.startsWith("v0=")) {
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
}
