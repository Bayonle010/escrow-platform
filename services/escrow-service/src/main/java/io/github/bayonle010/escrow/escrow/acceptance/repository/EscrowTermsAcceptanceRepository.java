package io.github.bayonle010.escrow.escrow.acceptance.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.bayonle010.escrow.escrow.acceptance.entity.EscrowTermsAcceptanceEntity;

public interface EscrowTermsAcceptanceRepository
        extends JpaRepository<EscrowTermsAcceptanceEntity, UUID> {
}
