package com.mydata.connectors.local;

import com.mydata.connectors.core.DataSourceConnector;
import com.mydata.connectors.core.DocumentHandler;
import com.mydata.connectors.core.RawAclEntry;
import com.mydata.connectors.core.RawContent;
import com.mydata.connectors.core.RawExternalDocument;
import com.mydata.connectors.core.SyncCursor;
import com.mydata.datasources.DataSourceEntity;
import com.mydata.datasources.DataSourceType;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Component
public class LocalTextConnector implements DataSourceConnector {
    private static final String MIME_TYPE = "text/plain";

    @Override
    public DataSourceType supports() {
        return DataSourceType.LOCAL_TEXT;
    }

    @Override
    public SyncCursor fetchChanges(DataSourceEntity dataSource, SyncCursor cursor, DocumentHandler handler) {
        String externalId = requiredConfig(dataSource, "externalId");
        String title = requiredConfig(dataSource, "title");
        String content = requiredConfig(dataSource, "content");
        String principalKey = requiredConfig(dataSource, "principalKey");

        handler.handle(new RawExternalDocument(
            externalId,
            DataSourceType.LOCAL_TEXT,
            title,
            null,
            MIME_TYPE,
            null,
            null,
            sha256(content),
            Map.of(),
            new RawContent(content, MIME_TYPE),
            List.of(new RawAclEntry(principalKey, "READ", false, "MANUAL"))
        ));
        return cursor;
    }

    private String requiredConfig(DataSourceEntity dataSource, String key) {
        String value = dataSource.configValue(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing LOCAL_TEXT config value: " + key);
        }
        return value;
    }

    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
