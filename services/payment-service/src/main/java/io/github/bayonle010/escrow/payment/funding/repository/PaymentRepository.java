package io.github.bayonle010.escrow.payment.funding.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.bayonle010.escrow.payment.funding.entity.PaymentEntity;

public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {

    Optional<PaymentEntity> findByPayerIdAndIdempotencyKey(UUID payerId, String idempotencyKey);

    Optional<PaymentEntity> findByEscrowId(UUID escrowId);
}
