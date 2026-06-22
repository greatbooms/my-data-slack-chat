package com.mydata.users;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByEmail(String email);

    Optional<UserEntity> findByIdAndDeletedAtIsNull(UUID id);

    Optional<UserEntity> findByEmailAndDeletedAtIsNull(String email);

    List<UserEntity> findByDeletedAtIsNullOrderByCreatedAtDesc();
}
