package com.mydata.connectors.local;

import com.mydata.connectors.core.ConnectorDocumentEvent;
import com.mydata.connectors.core.ConnectorEventSink;
import com.mydata.connectors.core.ConnectorItemReference;
import com.mydata.connectors.core.ConnectorItemType;
import com.mydata.connectors.core.DataSourceConnector;
import com.mydata.connectors.core.DataSourceSnapshot;
import com.mydata.connectors.core.RawAclEntry;
import com.mydata.connectors.core.RawContent;
import com.mydata.connectors.core.RawExternalDocument;
import com.mydata.connectors.core.SyncCursor;
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
    public SyncCursor fetchChanges(DataSourceSnapshot dataSource, ConnectorEventSink sink) {
        String externalId = requiredConfig(dataSource, "externalId");
        String title = requiredConfig(dataSource, "title");
        String content = requiredConfig(dataSource, "content");
        String principalKey = requiredConfig(dataSource, "principalKey");
        String uri = optionalConfig(dataSource, "uri");

        RawExternalDocument document = new RawExternalDocument(
            externalId,
            DataSourceType.LOCAL_TEXT,
            title,
            uri,
            MIME_TYPE,
            null,
            null,
            sha256(content),
            Map.of(),
            new RawContent(content, MIME_TYPE),
            List.of(new RawAclEntry(principalKey, "READ", false, "MANUAL"))
        );
        sink.onDocument(new ConnectorDocumentEvent(
            document,
            new ConnectorItemReference(ConnectorItemType.DATA_SOURCE, externalId, title, List.of(title))
        ));
        return dataSource.cursor();
    }

    private String requiredConfig(DataSourceSnapshot dataSource, String key) {
        String value = dataSource.configValue(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("LOCAL_TEXT 설정값이 없습니다: " + key);
        }
        return value;
    }

    private String optionalConfig(DataSourceSnapshot dataSource, String key) {
        String value = dataSource.configValue(key);
        return value == null || value.isBlank() ? null : value;
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
