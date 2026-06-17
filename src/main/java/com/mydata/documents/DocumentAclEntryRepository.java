package com.mydata.documents;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentAclEntryRepository extends JpaRepository<DocumentAclEntryEntity, UUID> {
    List<DocumentAclEntryEntity> findByDocumentId(UUID documentId);
}
