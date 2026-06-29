package com.mydata.retrieval;

import com.mydata.embeddings.DocumentEmbeddingRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
public class PgVectorSearchRepository {
    private final JdbcTemplate jdbcTemplate;
    private final DocumentEmbeddingRepository embeddings;

    public PgVectorSearchRepository(JdbcTemplate jdbcTemplate, DocumentEmbeddingRepository embeddings) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddings = embeddings;
    }

    public List<RetrievedChunk> search(
        UUID workspaceId,
        List<String> principalKeys,
        String model,
        float[] queryEmbedding,
        int limit
    ) {
        String vector = embeddings.vectorLiteral(queryEmbedding);
        return jdbcTemplate.execute((ConnectionCallback<List<RetrievedChunk>>) connection -> {
            Array principals = connection.createArrayOf("text", principalKeys.toArray());
            try (PreparedStatement statement = connection.prepareStatement(
                """
                SELECT
                  c.id AS chunk_id,
                  c.content,
                  d.title,
                  d.uri,
                  d.source_type,
                  e.embedding <=> CAST(? AS vector) AS distance
                FROM document_embeddings e
                JOIN document_chunks c ON c.id = e.chunk_id
                JOIN external_documents d ON d.id = c.document_id
                JOIN data_sources ds ON ds.id = d.data_source_id
                JOIN workspaces w ON w.id = d.workspace_id
                WHERE e.embedding_model = ?
                  AND d.workspace_id = ?
                  AND d.deleted_at IS NULL
                  AND ds.deleted_at IS NULL
                  AND w.deleted_at IS NULL
                  AND EXISTS (
                    SELECT 1
                    FROM document_acl_entries acl
                    WHERE acl.document_id = d.id
                      AND acl.permission = 'READ'
                      AND acl.principal_key = ANY (?)
                  )
                ORDER BY e.embedding <=> CAST(? AS vector)
                LIMIT ?
                """
            )) {
                statement.setString(1, vector);
                statement.setString(2, model);
                statement.setObject(3, workspaceId);
                statement.setArray(4, principals);
                statement.setString(5, vector);
                statement.setInt(6, limit);

                List<RetrievedChunk> chunks = new ArrayList<>();
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        chunks.add(new RetrievedChunk(
                            resultSet.getObject("chunk_id", UUID.class),
                            resultSet.getString("content"),
                            resultSet.getString("title"),
                            resultSet.getString("uri"),
                            resultSet.getString("source_type"),
                            resultSet.getDouble("distance")
                        ));
                    }
                }
                return chunks;
            } finally {
                principals.free();
            }
        });
    }
}
