package io.github.bayonle010.escrow.escrow.creation.repository;

import java.util.UUID;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.bayonle010.escrow.escrow.creation.entity.EscrowEntity;

public interface EscrowRepository extends JpaRepository<EscrowEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT escrow FROM EscrowEntity escrow WHERE escrow.escrowId = :escrowId")
    Optional<EscrowEntity> findByIdForUpdate(@Param("escrowId") UUID escrowId);
}
