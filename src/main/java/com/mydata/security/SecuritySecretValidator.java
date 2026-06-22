package com.mydata.security;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
public class SecuritySecretValidator {
    private static final String LOCAL_SIGNING_SECRET = "local-signing-secret";

    private final Environment environment;
    private final String slackSigningSecret;

    public SecuritySecretValidator(
        Environment environment,
        @Value("${my-data.slack.signing-secret}") String slackSigningSecret
    ) {
        this.environment = environment;
        this.slackSigningSecret = slackSigningSecret;
    }

    @PostConstruct
    void validate() {
        rejectBlank("my-data.slack.signing-secret", slackSigningSecret);
        if (allowsLocalDefaults()) {
            return;
        }
        if (LOCAL_SIGNING_SECRET.equals(slackSigningSecret)) {
            throw new IllegalStateException("로컬 기본 보안값은 local 또는 test 프로필에서만 사용할 수 있습니다");
        }
    }

    private boolean allowsLocalDefaults() {
        return environment.acceptsProfiles(Profiles.of("local", "test"));
    }

    private static void rejectBlank(String propertyName, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(propertyName + " 값은 비어 있을 수 없습니다");
        }
    }
}
