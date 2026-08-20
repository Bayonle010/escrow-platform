package io.github.bayonle010.escrow.identity.registration.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.bayonle010.escrow.identity.registration.entity.UserEntity;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {
}
