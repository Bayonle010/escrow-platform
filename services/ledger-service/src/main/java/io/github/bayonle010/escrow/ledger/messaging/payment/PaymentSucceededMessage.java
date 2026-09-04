package io.github.bayonle010.escrow.ledger.messaging.payment;

import java.time.Instant;
import java.util.UUID;

public record PaymentSucceededMessage(
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
        String status,
        long aggregateVersion,
        UUID correlationId) {
}
