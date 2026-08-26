package io.github.bayonle010.escrow.escrow.creation.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.bayonle010.escrow.escrow.creation.entity.EscrowTermsEntity;

public interface EscrowTermsRepository extends JpaRepository<EscrowTermsEntity, UUID> {

    Optional<EscrowTermsEntity> findByEscrowIdAndTermsVersion(UUID escrowId, int termsVersion);
}
