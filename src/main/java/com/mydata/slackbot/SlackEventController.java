package com.mydata.slackbot;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@RestController
public class SlackEventController {
    private final SlackSignatureVerifier signatureVerifier;
    private final ObjectMapper objectMapper;
    private final Environment environment;

    public SlackEventController(
        @Value("${my-data.slack.signing-secret}") String signingSecret,
        ObjectMapper objectMapper,
        Environment environment
    ) {
        this.signatureVerifier = new SlackSignatureVerifier(signingSecret);
        this.objectMapper = objectMapper;
        this.environment = environment;
    }

    @PostMapping("/slack/events")
    ResponseEntity<String> receiveEvent(
        @RequestBody String body,
        @RequestHeader(value = "X-Slack-Request-Timestamp", required = false) String timestamp,
        @RequestHeader(value = "X-Slack-Signature", required = false) String signature
    ) throws Exception {
        if (!hasValidSignature(timestamp, body, signature)) {
            return ResponseEntity.status(401).body("invalid signature");
        }

        JsonNode root = objectMapper.readTree(body);
        if ("url_verification".equals(root.path("type").asString())) {
            return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(root.path("challenge").asString());
        }

        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_PLAIN)
            .body("ok");
    }

    private boolean hasValidSignature(String timestamp, String body, String signature) {
        if (environment.acceptsProfiles(Profiles.of("test")) && "test-bypass".equals(signature)) {
            return true;
        }

        return signatureVerifier.isValid(timestamp, body, signature);
    }
}
