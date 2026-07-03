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
        List<String> queryTerms,
        int limit
    ) {
        String vector = embeddings.vectorLiteral(queryEmbedding);
        List<String> safeQueryTerms = queryTerms == null ? List.of() : queryTerms.stream()
            .filter(term -> term != null && !term.isBlank())
            .map(String::trim)
            .toList();
        return jdbcTemplate.execute((ConnectionCallback<List<RetrievedChunk>>) connection -> {
            Array principals = connection.createArrayOf("text", principalKeys.toArray());
            Array terms = connection.createArrayOf("text", safeQueryTerms.toArray());
            try (PreparedStatement statement = connection.prepareStatement(
                """
                WITH visible_chunks AS (
                  SELECT
                    c.id AS chunk_id,
                    c.content,
                    d.title,
                    d.uri,
                    d.source_type,
                    d.external_created_at,
                    d.metadata_json::text AS document_metadata_json,
                    e.embedding <=> CAST(? AS vector) AS distance,
                    (
                      SELECT count(*)
                      FROM unnest(?::text[]) AS term(value)
                      WHERE lower(c.content) LIKE '%' || lower(term.value) || '%'
                         OR lower(coalesce(d.title, '')) LIKE '%' || lower(term.value) || '%'
                    ) AS lexical_matches
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
                  )
                SELECT
                  chunk_id,
                  content,
                  title,
                  uri,
                  source_type,
                  external_created_at,
                  document_metadata_json,
                  distance
                FROM visible_chunks
                ORDER BY
                  CASE WHEN cardinality(?::text[]) > 0 AND lexical_matches > 0 THEN 0 ELSE 1 END,
                  lexical_matches DESC,
                  CASE WHEN lexical_matches > 0 THEN external_created_at END DESC NULLS LAST,
                  distance
                LIMIT ?
                """
            )) {
                statement.setString(1, vector);
                statement.setArray(2, terms);
                statement.setString(3, model);
                statement.setObject(4, workspaceId);
                statement.setArray(5, principals);
                statement.setArray(6, terms);
                statement.setInt(7, limit);

                List<RetrievedChunk> chunks = new ArrayList<>();
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        chunks.add(new RetrievedChunk(
                            resultSet.getObject("chunk_id", UUID.class),
                            resultSet.getString("content"),
                            resultSet.getString("title"),
                            resultSet.getString("uri"),
                            resultSet.getString("source_type"),
                            resultSet.getDouble("distance"),
                            resultSet.getString("external_created_at"),
                            resultSet.getString("document_metadata_json")
                        ));
                    }
                }
                return chunks;
            } finally {
                principals.free();
                terms.free();
            }
        });
    }
}
