package io.github.bayonle010.escrow.escrow.creation.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.bayonle010.escrow.escrow.creation.entity.EscrowEntity;

public interface EscrowRepository extends JpaRepository<EscrowEntity, UUID> {
}
