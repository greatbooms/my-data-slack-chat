package com.mydata.connectors.slack;

import com.mydata.auth.PrincipalKeys;
import com.mydata.connectors.core.RawAclEntry;
import com.mydata.connectors.core.RawContent;
import com.mydata.connectors.core.RawExternalDocument;
import com.mydata.datasources.DataSourceEntity;
import com.mydata.datasources.DataSourceType;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class SlackRawDocumentFactory {
    private static final String MIME_TYPE = "text/plain";

    public RawExternalDocument toRawDocument(DataSourceEntity dataSource, SlackClient.SlackMessage message) {
        String text = documentText(message);
        String permalink = permalink(message, blankToNull(dataSource.configValue(SlackChannelConnector.WORKSPACE_URL_CONFIG_KEY)));
        return new RawExternalDocument(
            externalId(message),
            DataSourceType.SLACK,
            "Slack " + message.channelId() + " " + message.messageTs(),
            permalink,
            MIME_TYPE,
            message.createdAt(),
            message.createdAt(),
            sha256(text),
            metadata(message, permalink),
            new RawContent(text, MIME_TYPE),
            List.of(new RawAclEntry(principalKey(dataSource), "READ", false, "SLACK"))
        );
    }

    private Map<String, Object> metadata(SlackClient.SlackMessage message, String permalink) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("slackChannelId", message.channelId());
        putIfPresent(metadata, "slackUserId", message.userId());
        metadata.put("slackMessageTs", message.messageTs());
        metadata.put("slackThreadTs", threadTs(message));
        metadata.put("slackIsThreadReply", message.threadReply());
        putIfPresent(metadata, "slackPermalink", permalink);
        return metadata;
    }

    private String permalink(SlackClient.SlackMessage message, String workspaceUrl) {
        if (workspaceUrl == null) {
            return blankToNull(message.permalink());
        }

        return removeTrailingSlash(workspaceUrl)
            + "/archives/"
            + message.channelId()
            + "/p"
            + message.messageTs().replace(".", "");
    }

    private String documentText(SlackClient.SlackMessage message) {
        return """
            Slack channel: %s
            User: %s
            Timestamp: %s
            Thread: %s
            Message:
            %s
            """.formatted(
            message.channelId(),
            blankToDefault(message.userId(), "UNKNOWN"),
            message.messageTs(),
            threadTs(message),
            blankToDefault(message.text(), "")
        ).stripTrailing();
    }

    private String threadTs(SlackClient.SlackMessage message) {
        return blankToDefault(message.threadTs(), message.messageTs());
    }

    private String principalKey(DataSourceEntity dataSource) {
        return switch (dataSource.getVisibility()) {
            case PRIVATE -> PrincipalKeys.user(dataSource.getOwnerUserId());
            case WORKSPACE -> PrincipalKeys.workspace(dataSource.getWorkspaceId());
        };
    }

    private String externalId(SlackClient.SlackMessage message) {
        return message.channelId() + ":" + message.messageTs();
    }

    private void putIfPresent(Map<String, Object> metadata, String key, String value) {
        if (value != null && !value.isBlank()) {
            metadata.put(key, value);
        }
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String removeTrailingSlash(String value) {
        String normalized = value;
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다", exception);
        }
    }
}
