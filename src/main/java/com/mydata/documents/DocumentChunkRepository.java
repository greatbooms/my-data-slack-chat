package com.mydata.documents;

import com.mydata.embeddings.EmbeddingCoverageProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunkEntity, UUID> {
    List<DocumentChunkEntity> findByDocumentIdOrderByChunkIndex(UUID documentId);

    @Query(value = """
        SELECT count(*) AS "totalChunks",
               count(*) FILTER (WHERE e.chunk_id IS NOT NULL) AS "coveredChunks"
        FROM document_chunks c
        JOIN external_documents d ON d.id = c.document_id
        LEFT JOIN document_embeddings e
          ON e.chunk_id = c.id AND e.embedding_model = :model
        WHERE d.data_source_id = :dataSourceId
          AND d.deleted_at IS NULL
        """, nativeQuery = true)
    EmbeddingCoverageProjection coverageByDataSource(
        @Param("dataSourceId") UUID dataSourceId,
        @Param("model") String model
    );

    @Query("""
        SELECT chunk FROM DocumentChunkEntity chunk
        WHERE chunk.document.dataSourceId = :dataSourceId
          AND chunk.document.deletedAt IS NULL
        ORDER BY chunk.document.id, chunk.chunkIndex
        """)
    List<DocumentChunkEntity> findByDataSource(@Param("dataSourceId") UUID dataSourceId);
}
