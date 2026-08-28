package io.github.bayonle010.escrow.payment.funding.event;

import java.time.Instant;
import java.util.UUID;

import io.github.bayonle010.escrow.payment.funding.domain.PaymentStatus;

public record PaymentSucceededPayload(
        String eventType,
        int eventVersion,
        Instant occurredAt,
        UUID paymentId,
        UUID escrowId,
        UUID payerId,
        long amountMinor,
        String currency,
        String provider,
        String providerReference,
        PaymentStatus status,
        long aggregateVersion,
        UUID correlationId) {
}
