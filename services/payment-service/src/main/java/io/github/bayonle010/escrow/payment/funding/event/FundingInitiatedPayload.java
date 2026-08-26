package io.github.bayonle010.escrow.payment.funding.event;

import java.time.Instant;
import java.util.UUID;

import io.github.bayonle010.escrow.payment.funding.domain.PaymentStatus;

public record FundingInitiatedPayload(
        String eventType,
        int eventVersion,
        UUID paymentId,
        UUID escrowId,
        UUID payerId,
        long amountMinor,
        String currency,
        String provider,
        PaymentStatus status,
        Instant occurredAt,
        UUID correlationId) {
}
