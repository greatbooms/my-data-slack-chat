package com.mydata.admin;

import com.mydata.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class AdminSchemaMigrationTest extends PostgresIntegrationTest {
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void createsAdminUserAndDataSourceColumnsWithExpectedContracts() {
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

        ColumnContract userRole = column("users", "role");
        ColumnContract userStatus = column("users", "status");
        ColumnContract userDeletedAt = column("users", "deleted_at");
        ColumnContract userUpdatedAt = column("users", "updated_at");
        ColumnContract dataSourceOwner = column("data_sources", "owner_user_id");
        ColumnContract dataSourceVisibility = column("data_sources", "visibility");
        ColumnContract dataSourceDeletedAt = column("data_sources", "deleted_at");

        assertThat(userRole.dataType()).isEqualTo("text");
        assertThat(userRole.isNullable()).isEqualTo("NO");
        assertThat(userRole.columnDefault()).contains("'USER'::text");
        assertThat(userStatus.dataType()).isEqualTo("text");
        assertThat(userStatus.isNullable()).isEqualTo("NO");
        assertThat(userStatus.columnDefault()).contains("'ACTIVE'::text");
        assertThat(userDeletedAt.dataType()).isEqualTo("timestamp with time zone");
        assertThat(userUpdatedAt.dataType()).isEqualTo("timestamp with time zone");
        assertThat(userUpdatedAt.isNullable()).isEqualTo("NO");

        assertThat(dataSourceOwner.dataType()).isEqualTo("uuid");
        assertThat(dataSourceVisibility.dataType()).isEqualTo("text");
        assertThat(dataSourceVisibility.isNullable()).isEqualTo("NO");
        assertThat(dataSourceVisibility.columnDefault()).contains("'PRIVATE'::text");
        assertThat(dataSourceDeletedAt.dataType()).isEqualTo("timestamp with time zone");

        Integer ownerForeignKeys = jdbcTemplate.queryForObject("""
            SELECT count(*)
            FROM information_schema.table_constraints constraint_info
            JOIN information_schema.key_column_usage key_usage
              ON constraint_info.constraint_name = key_usage.constraint_name
             AND constraint_info.table_schema = key_usage.table_schema
            WHERE constraint_info.table_name = 'data_sources'
              AND constraint_info.constraint_type = 'FOREIGN KEY'
              AND key_usage.column_name = 'owner_user_id'
            """, Integer.class);

        assertThat(ownerForeignKeys).isEqualTo(1);
    }

    private ColumnContract column(String tableName, String columnName) {
        return jdbcTemplate.queryForObject("""
            SELECT data_type, is_nullable, column_default
            FROM information_schema.columns
            WHERE table_name = ?
              AND column_name = ?
            """, (resultSet, rowNumber) -> new ColumnContract(
            resultSet.getString("data_type"),
            resultSet.getString("is_nullable"),
            resultSet.getString("column_default")
        ), tableName, columnName);
    }

    private record ColumnContract(String dataType, String isNullable, String columnDefault) {
    }
}
