package io.github.bayonle010.escrow.payment.funding.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.bayonle010.escrow.payment.funding.builder.FundingInitiatedEventBuilder;
import io.github.bayonle010.escrow.payment.funding.domain.EscrowFundingSnapshot;
import io.github.bayonle010.escrow.payment.funding.domain.PaymentStatus;
import io.github.bayonle010.escrow.payment.funding.entity.PaymentEntity;
import io.github.bayonle010.escrow.payment.funding.repository.OutboxEventRepository;
import io.github.bayonle010.escrow.payment.funding.repository.PaymentRepository;

@Service
public class PaymentPersistenceService {

    private static final String INITIAL_PROVIDER = "SIMULATED";

    private final PaymentRepository paymentRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final FundingInitiatedEventBuilder eventBuilder;

    public PaymentPersistenceService(
            PaymentRepository paymentRepository,
            OutboxEventRepository outboxEventRepository,
            FundingInitiatedEventBuilder eventBuilder) {
        this.paymentRepository = paymentRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.eventBuilder = eventBuilder;
    }

    @Transactional
    public PaymentEntity create(
            EscrowFundingSnapshot escrow,
            UUID payerId,
            String idempotencyKey,
            String requestFingerprint,
            Instant createdAt,
            UUID correlationId) {
        PaymentEntity payment = paymentRepository.saveAndFlush(PaymentEntity.builder()
                .escrowId(escrow.id())
                .payerId(payerId)
                .amountMinor(escrow.amountMinor())
                .currency(escrow.currency())
                .provider(INITIAL_PROVIDER)
                .status(PaymentStatus.PROCESSING)
                .idempotencyKey(idempotencyKey)
                .requestFingerprint(requestFingerprint)
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .build());
        outboxEventRepository.save(eventBuilder.build(payment, correlationId));
        outboxEventRepository.flush();
        return payment;
    }
}
