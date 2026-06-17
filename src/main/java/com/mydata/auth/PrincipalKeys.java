package com.mydata.auth;

import java.util.Locale;
import java.util.UUID;

public final class PrincipalKeys {
    private PrincipalKeys() {
    }

    public static String user(UUID userId) {
        return "USER:" + userId;
    }

    public static String workspace(UUID workspaceId) {
        return "WORKSPACE:" + workspaceId;
    }

    public static String slackUser(String slackWorkspaceId, String slackUserId) {
        return "SLACK_USER:" + slackWorkspaceId + ":" + slackUserId;
    }

    public static String slackWorkspace(String slackWorkspaceId) {
        return "SLACK_WORKSPACE:" + slackWorkspaceId;
    }

    public static String slackChannel(String channelId) {
        return "SLACK_CHANNEL:" + channelId;
    }

    public static String googleUser(String email) {
        return "GOOGLE_USER:" + email.toLowerCase(Locale.ROOT);
    }
}
