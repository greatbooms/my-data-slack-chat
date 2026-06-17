package com.mydata.documents;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ExternalDocumentRepository extends JpaRepository<ExternalDocumentEntity, UUID> {
    Optional<ExternalDocumentEntity> findByDataSourceIdAndExternalId(UUID dataSourceId, String externalId);
}
