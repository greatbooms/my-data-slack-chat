package com.mydata.workspaces;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceRepository extends JpaRepository<WorkspaceEntity, UUID> {
    List<WorkspaceEntity> findByDeletedAtIsNullOrderByCreatedAtDesc();

    Optional<WorkspaceEntity> findByIdAndDeletedAtIsNull(UUID id);

    List<WorkspaceEntity> findByOwnerUserIdAndDeletedAtIsNull(UUID ownerUserId);
}
