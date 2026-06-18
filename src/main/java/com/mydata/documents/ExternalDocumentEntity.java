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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Entity
@Table(name = "external_documents")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExternalDocumentEntity extends BaseEntity {
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
        this.sourceType = sourceType;
        this.title = title;
        this.uri = uri;
        this.contentHash = contentHash;
    }
}
