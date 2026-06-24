package com.mydata.security;

import com.mydata.slackbot.SlackBotProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
public class SecuritySecretValidator {
    private static final String LOCAL_SIGNING_SECRET = "local-signing-secret";

    private final Environment environment;
    private final SlackBotProperties slack;

    public SecuritySecretValidator(
        Environment environment,
        SlackBotProperties slack
    ) {
        this.environment = environment;
        this.slack = slack;
    }

    @PostConstruct
    void validate() {
        if (slack.httpEventsEnabled()) {
            rejectBlank("my-data.slack.signing-secret", slack.signingSecret());
            if (!allowsLocalDefaults() && LOCAL_SIGNING_SECRET.equals(slack.signingSecret())) {
                throw new IllegalStateException("로컬 기본 보안값은 local 또는 test 프로필에서만 사용할 수 있습니다");
            }
        }

        if (slack.socketModeEnabled()) {
            rejectBlank("my-data.slack.app-token", slack.appToken());
            rejectBlank("my-data.slack.bot-token", slack.botToken());
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
