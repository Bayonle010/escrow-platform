package io.github.bayonle010.escrow.payment.funding.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.bayonle010.escrow.payment.funding.builder.PaymentSucceededEventBuilder;
import io.github.bayonle010.escrow.payment.funding.domain.ConfirmedPayment;
import io.github.bayonle010.escrow.payment.funding.domain.PaymentStatus;
import io.github.bayonle010.escrow.payment.funding.entity.PaymentEntity;
import io.github.bayonle010.escrow.payment.funding.repository.OutboxEventRepository;
import io.github.bayonle010.escrow.payment.funding.repository.PaymentRepository;
import io.github.bayonle010.escrow.payment.shared.api.ErrorCode;
import io.github.bayonle010.escrow.payment.shared.exception.PaymentApiException;

@Service
public class PaymentConfirmationService {

    private final PaymentRepository paymentRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final PaymentSucceededEventBuilder eventBuilder;
    private final Clock clock;

    public PaymentConfirmationService(
            PaymentRepository paymentRepository,
            OutboxEventRepository outboxEventRepository,
            PaymentSucceededEventBuilder eventBuilder,
            Clock clock) {
        this.paymentRepository = paymentRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.eventBuilder = eventBuilder;
        this.clock = clock;
    }

    @Transactional
    public ConfirmedPayment confirm(
            UUID paymentId,
            String providerReference,
            UUID correlationId) {
        String normalizedReference = providerReference.trim();
        PaymentEntity payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> paymentNotFound(paymentId));

        if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
            if (normalizedReference.equals(payment.getProviderReference())) {
                return toDomain(payment, true);
            }
            throw outcomeConflict(payment);
        }
        if (payment.getStatus() != PaymentStatus.PROCESSING) {
            throw outcomeConflict(payment);
        }

        paymentRepository.findByProviderAndProviderReference(
                        payment.getProvider(),
                        normalizedReference)
                .filter(existing -> !existing.getPaymentId().equals(paymentId))
                .ifPresent(existing -> {
                    throw providerReferenceReused(normalizedReference);
                });

        Instant confirmedAt = clock.instant();
        payment.succeed(normalizedReference, confirmedAt);
        try {
            paymentRepository.saveAndFlush(payment);
        } catch (DataIntegrityViolationException exception) {
            throw providerReferenceReused(normalizedReference);
        }
        outboxEventRepository.save(eventBuilder.build(payment, correlationId));
        outboxEventRepository.flush();
        return toDomain(payment, false);
    }

    private PaymentApiException paymentNotFound(UUID paymentId) {
        return new PaymentApiException(
                ErrorCode.PAYMENT_NOT_FOUND,
                HttpStatus.NOT_FOUND,
                "paymentId",
                "Payment " + paymentId + " was not found.");
    }

    private PaymentApiException outcomeConflict(PaymentEntity payment) {
        return new PaymentApiException(
                ErrorCode.PAYMENT_OUTCOME_CONFLICT,
                HttpStatus.CONFLICT,
                "providerReference",
                "Payment " + payment.getPaymentId() + " already has status "
                        + payment.getStatus() + " and cannot accept this confirmation.");
    }

    private PaymentApiException providerReferenceReused(String providerReference) {
        return new PaymentApiException(
                ErrorCode.PROVIDER_REFERENCE_REUSED,
                HttpStatus.CONFLICT,
                "providerReference",
                "Provider reference " + providerReference + " is already assigned to another payment.");
    }

    private ConfirmedPayment toDomain(PaymentEntity payment, boolean replayed) {
        return new ConfirmedPayment(
                payment.getPaymentId(),
                payment.getEscrowId(),
                payment.getPayerId(),
                payment.getAmountMinor(),
                payment.getCurrency(),
                payment.getProvider(),
                payment.getProviderReference(),
                payment.getStatus(),
                payment.getUpdatedAt(),
                replayed);
    }
}
