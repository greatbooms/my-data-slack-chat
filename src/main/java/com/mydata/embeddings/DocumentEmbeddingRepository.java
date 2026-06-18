package com.mydata.embeddings;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class DocumentEmbeddingRepository {
    private final JdbcTemplate jdbcTemplate;

    public DocumentEmbeddingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void upsert(UUID chunkId, String model, float[] embedding) {
        jdbcTemplate.update(
            """
            INSERT INTO document_embeddings (chunk_id, embedding_model, embedding)
            VALUES (?, ?, CAST(? AS vector))
            ON CONFLICT (chunk_id, embedding_model)
            DO UPDATE SET embedding = EXCLUDED.embedding
            """,
            chunkId,
            model,
            vectorLiteral(embedding)
        );
    }

    public boolean existsByChunkIdAndModel(UUID chunkId, String model) {
        Integer count = jdbcTemplate.queryForObject(
            """
            SELECT count(*)
            FROM document_embeddings
            WHERE chunk_id = ?
              AND embedding_model = ?
            """,
            Integer.class,
            chunkId,
            model
        );
        return count != null && count > 0;
    }

    public String vectorLiteral(float[] vector) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(vector[i]);
        }
        return builder.append(']').toString();
    }
}
