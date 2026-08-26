package io.github.bayonle010.escrow.payment.funding.domain;

import java.time.Instant;
import java.util.UUID;

public record InitiatedPayment(
        UUID paymentId,
        UUID escrowId,
        UUID payerId,
        long amountMinor,
        String currency,
        String provider,
        PaymentStatus status,
        Instant createdAt,
        boolean replayed) {
}
