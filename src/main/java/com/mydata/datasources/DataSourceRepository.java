package com.mydata.datasources;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DataSourceRepository extends JpaRepository<DataSourceEntity, UUID> {
    List<DataSourceEntity> findByWorkspaceId(UUID workspaceId);

    Optional<DataSourceEntity> findByIdAndDeletedAtIsNull(UUID id);

    List<DataSourceEntity> findByDeletedAtIsNullOrderByCreatedAtDesc();
}
