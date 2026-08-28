package io.github.bayonle010.escrow.payment.funding.domain;

import java.time.Instant;
import java.util.UUID;

public record ConfirmedPayment(
        UUID paymentId,
        UUID escrowId,
        UUID payerId,
        long amountMinor,
        String currency,
        String provider,
        String providerReference,
        PaymentStatus status,
        Instant confirmedAt,
        boolean replayed) {
}
