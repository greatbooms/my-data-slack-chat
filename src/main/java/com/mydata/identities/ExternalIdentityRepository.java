package com.mydata.identities;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExternalIdentityRepository extends JpaRepository<ExternalIdentityEntity, UUID> {
    List<ExternalIdentityEntity> findAllByOrderByCreatedAtDesc();

    Optional<ExternalIdentityEntity> findByProviderAndExternalWorkspaceIdAndExternalUserId(
        ExternalIdentityProvider provider,
        String externalWorkspaceId,
        String externalUserId
    );
}
