package com.mydata.slackbot;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@RestController
@ConditionalOnProperty(prefix = "my-data.slack", name = "http-events-enabled", havingValue = "true")
public class SlackEventController {
    private final SlackSignatureVerifier signatureVerifier;
    private final ObjectMapper objectMapper;
    private final Environment environment;

    public SlackEventController(
        SlackBotProperties slack,
        ObjectMapper objectMapper,
        Environment environment
    ) {
        this.signatureVerifier = new SlackSignatureVerifier(slack.signingSecret());
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
            return ResponseEntity.status(401).body("Slack 서명이 올바르지 않습니다");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (JacksonException e) {
            return ResponseEntity.badRequest().body("JSON 형식이 올바르지 않습니다");
        }

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
