package io.github.bayonle010.escrow.payment.funding.repository;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.bayonle010.escrow.payment.funding.entity.PaymentEntity;

public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {

    Optional<PaymentEntity> findByPayerIdAndIdempotencyKey(UUID payerId, String idempotencyKey);

    Optional<PaymentEntity> findByEscrowId(UUID escrowId);

    Optional<PaymentEntity> findByProviderAndProviderReference(
            String provider,
            String providerReference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payment from PaymentEntity payment where payment.paymentId = :paymentId")
    Optional<PaymentEntity> findByIdForUpdate(@Param("paymentId") UUID paymentId);
}
