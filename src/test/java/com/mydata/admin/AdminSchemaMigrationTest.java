package com.mydata.admin;

import com.mydata.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class AdminSchemaMigrationTest extends PostgresIntegrationTest {
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void createsAdminUserAndDataSourceColumns() {
        Integer usersColumns = jdbcTemplate.queryForObject("""
            SELECT count(*) FROM information_schema.columns
            WHERE table_name = 'users'
              AND column_name IN ('password_hash', 'role', 'status', 'deleted_at', 'updated_at')
            """, Integer.class);
        Integer dataSourceColumns = jdbcTemplate.queryForObject("""
            SELECT count(*) FROM information_schema.columns
            WHERE table_name = 'data_sources'
              AND column_name IN ('owner_user_id', 'visibility', 'deleted_at')
            """, Integer.class);

        assertThat(usersColumns).isEqualTo(5);
        assertThat(dataSourceColumns).isEqualTo(3);
    }
}
