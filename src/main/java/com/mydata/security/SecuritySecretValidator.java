package com.mydata.security;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
public class SecuritySecretValidator {
    private static final String LOCAL_ADMIN_TOKEN = "local-admin-token";
    private static final String LOCAL_SIGNING_SECRET = "local-signing-secret";

    private final Environment environment;
    private final String adminToken;
    private final String slackSigningSecret;

    public SecuritySecretValidator(
        Environment environment,
        @Value("${my-data.admin-token}") String adminToken,
        @Value("${my-data.slack.signing-secret}") String slackSigningSecret
    ) {
        this.environment = environment;
        this.adminToken = adminToken;
        this.slackSigningSecret = slackSigningSecret;
    }

    @PostConstruct
    void validate() {
        rejectBlank("my-data.admin-token", adminToken);
        rejectBlank("my-data.slack.signing-secret", slackSigningSecret);
        if (allowsLocalDefaults()) {
            return;
        }
        if (LOCAL_ADMIN_TOKEN.equals(adminToken) || LOCAL_SIGNING_SECRET.equals(slackSigningSecret)) {
            throw new IllegalStateException("local defaults are only allowed for local and test profiles");
        }
    }

    private boolean allowsLocalDefaults() {
        return environment.acceptsProfiles(Profiles.of("local", "test"));
    }

    private static void rejectBlank(String propertyName, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(propertyName + " must not be blank");
        }
    }
}
