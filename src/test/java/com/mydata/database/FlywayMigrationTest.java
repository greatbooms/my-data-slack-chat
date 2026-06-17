package com.mydata.database;

import com.mydata.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationTest extends PostgresIntegrationTest {
    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void createsRequiredTablesAndVectorExtension() {
        Integer tableCount = jdbcTemplate.queryForObject("""
            SELECT count(*) FROM information_schema.tables
            WHERE table_schema = 'public'
              AND table_name IN (
                'users',
                'workspaces',
                'data_sources',
                'external_documents',
                'document_acl_entries',
                'document_chunks',
                'document_embeddings',
                'ingestion_jobs',
                'chat_sessions',
                'chat_messages'
              )
            """, Integer.class);

        String vectorVersion = jdbcTemplate.queryForObject(
            "SELECT extversion FROM pg_extension WHERE extname = 'vector'",
            String.class
        );

        assertThat(tableCount).isEqualTo(10);
        assertThat(vectorVersion).isNotBlank();
    }
}
