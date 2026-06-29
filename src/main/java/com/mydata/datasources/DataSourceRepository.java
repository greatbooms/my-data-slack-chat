package com.mydata.datasources;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DataSourceRepository extends JpaRepository<DataSourceEntity, UUID> {
    List<DataSourceEntity> findByWorkspaceId(UUID workspaceId);

    Optional<DataSourceEntity> findByIdAndDeletedAtIsNull(UUID id);

    List<DataSourceEntity> findByDeletedAtIsNullOrderByCreatedAtDesc();

    @Query("""
        SELECT dataSource
        FROM DataSourceEntity dataSource
        WHERE dataSource.deletedAt IS NULL
          AND EXISTS (
            SELECT 1
            FROM WorkspaceEntity workspace
            WHERE workspace.id = dataSource.workspaceId
              AND workspace.deletedAt IS NULL
          )
        ORDER BY dataSource.createdAt DESC
        """)
    List<DataSourceEntity> findActiveOrderByCreatedAtDesc();

    @Query("""
        SELECT dataSource
        FROM DataSourceEntity dataSource
        WHERE dataSource.id = :id
          AND dataSource.deletedAt IS NULL
          AND EXISTS (
            SELECT 1
            FROM WorkspaceEntity workspace
            WHERE workspace.id = dataSource.workspaceId
              AND workspace.deletedAt IS NULL
          )
        """)
    Optional<DataSourceEntity> findActiveById(@Param("id") UUID id);
}
