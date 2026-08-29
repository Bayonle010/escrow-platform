package io.github.bayonle010.escrow.ledger.funding.domain;

import java.time.Instant;
import java.util.UUID;

public record PaymentSucceededEvent(
        UUID eventId,
        int eventVersion,
        Instant occurredAt,
        UUID paymentId,
        UUID escrowId,
        UUID payerId,
        long amountMinor,
        String currency,
        String provider,
        String providerReference,
        long aggregateVersion,
        UUID correlationId) {
}
