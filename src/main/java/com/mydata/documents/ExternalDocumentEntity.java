package com.mydata.documents;

import com.mydata.common.domain.BaseEntity;
import com.mydata.common.json.JsonMaps;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnTransformer;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Entity
@Table(name = "external_documents")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExternalDocumentEntity extends BaseEntity {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "data_source_id", nullable = false)
    private UUID dataSourceId;

    @Column(name = "external_id", nullable = false, columnDefinition = "text")
    private String externalId;

    @Column(name = "source_type", nullable = false, columnDefinition = "text")
    private String sourceType;

    @Column(nullable = false, columnDefinition = "text")
    private String title;

    @Column(columnDefinition = "text")
    private String uri;

    @Column(name = "content_hash", columnDefinition = "text")
    private String contentHash;

    @Column(name = "mime_type", columnDefinition = "text")
    private String mimeType;

    @Column(name = "external_created_at")
    private Instant externalCreatedAt;

    @Column(name = "external_updated_at")
    private Instant externalUpdatedAt;

    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = JsonMaps.EMPTY_OBJECT;

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DocumentAclEntryEntity> aclEntries = new ArrayList<>();

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DocumentChunkEntity> chunks = new ArrayList<>();

    public static ExternalDocumentEntity create(
        UUID workspaceId,
        UUID dataSourceId,
        String externalId,
        String sourceType,
        String title,
        String contentHash
    ) {
        return create(workspaceId, dataSourceId, externalId, sourceType, title, null, contentHash);
    }

    public static ExternalDocumentEntity create(
        UUID workspaceId,
        UUID dataSourceId,
        String externalId,
        String sourceType,
        String title,
        String uri,
        String contentHash
    ) {
        ExternalDocumentEntity document = new ExternalDocumentEntity();
        document.workspaceId = workspaceId;
        document.dataSourceId = dataSourceId;
        document.externalId = externalId;
        document.sourceType = sourceType;
        document.title = title;
        document.uri = uri;
        document.contentHash = contentHash;
        document.mimeType = null;
        document.externalCreatedAt = null;
        document.externalUpdatedAt = null;
        document.metadataJson = JsonMaps.EMPTY_OBJECT;
        return document;
    }

    public void addAcl(DocumentAclEntryEntity acl) {
        aclEntries.add(acl);
    }

    public void addChunk(DocumentChunkEntity chunk) {
        chunks.add(chunk);
    }

    public void updateFromIngestion(String sourceType, String title, String contentHash) {
        updateFromIngestion(sourceType, title, null, contentHash);
    }

    public void updateFromIngestion(String sourceType, String title, String uri, String contentHash) {
        updateFromIngestion(sourceType, title, uri, null, null, null, contentHash, Map.of());
    }

    public void updateFromIngestion(
        String sourceType,
        String title,
        String uri,
        String mimeType,
        Instant externalCreatedAt,
        Instant externalUpdatedAt,
        String contentHash,
        Map<String, Object> metadata
    ) {
        this.sourceType = sourceType;
        this.title = title;
        this.uri = uri;
        this.mimeType = mimeType;
        this.externalCreatedAt = externalCreatedAt;
        this.externalUpdatedAt = externalUpdatedAt;
        this.contentHash = contentHash;
        this.metadataJson = metadataJson(metadata);
    }

    private String metadataJson(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return JsonMaps.EMPTY_OBJECT;
        }

        try {
            return OBJECT_MAPPER.writeValueAsString(new LinkedHashMap<>(metadata));
        } catch (Exception exception) {
            throw new IllegalStateException("문서 메타데이터를 JSON으로 변환하지 못했습니다", exception);
        }
    }
}
