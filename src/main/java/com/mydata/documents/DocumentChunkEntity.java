package com.mydata.documents;

import com.mydata.common.domain.BaseEntity;
import com.mydata.common.json.JsonMaps;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnTransformer;

@Getter
@Entity
@Table(name = "document_chunks")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentChunkEntity extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private ExternalDocumentEntity document;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "token_count")
    private Integer tokenCount;

    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = JsonMaps.EMPTY_OBJECT;

    public static DocumentChunkEntity create(
        ExternalDocumentEntity document,
        int chunkIndex,
        String content,
        Integer tokenCount
    ) {
        DocumentChunkEntity chunk = new DocumentChunkEntity();
        chunk.document = document;
        chunk.chunkIndex = chunkIndex;
        chunk.content = content;
        chunk.tokenCount = tokenCount;
        chunk.metadataJson = JsonMaps.EMPTY_OBJECT;
        return chunk;
    }
}
