package com.mydata.auth;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public final class PrincipalKeys {
    private PrincipalKeys() {
    }

    public static String user(UUID userId) {
        Objects.requireNonNull(userId, "userId");

        return "USER:" + userId;
    }

    public static String workspace(UUID workspaceId) {
        Objects.requireNonNull(workspaceId, "workspaceId");

        return "WORKSPACE:" + workspaceId;
    }

    public static String slackUser(String slackWorkspaceId, String slackUserId) {
        Objects.requireNonNull(slackWorkspaceId, "slackWorkspaceId");
        Objects.requireNonNull(slackUserId, "slackUserId");

        return "SLACK_USER:" + slackWorkspaceId + ":" + slackUserId;
    }

    public static String slackWorkspace(String slackWorkspaceId) {
        Objects.requireNonNull(slackWorkspaceId, "slackWorkspaceId");

        return "SLACK_WORKSPACE:" + slackWorkspaceId;
    }

    public static String slackChannel(String slackWorkspaceId, String channelId) {
        Objects.requireNonNull(slackWorkspaceId, "slackWorkspaceId");
        Objects.requireNonNull(channelId, "channelId");

        return "SLACK_CHANNEL:" + slackWorkspaceId + ":" + channelId;
    }

    public static String googleUser(String email) {
        Objects.requireNonNull(email, "email");

        return "GOOGLE_USER:" + email.toLowerCase(Locale.ROOT);
    }
}
