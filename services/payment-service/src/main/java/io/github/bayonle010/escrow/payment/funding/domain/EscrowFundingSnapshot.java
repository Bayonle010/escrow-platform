package io.github.bayonle010.escrow.payment.funding.domain;

import java.time.Instant;
import java.util.UUID;

public record EscrowFundingSnapshot(
        UUID id,
        UUID buyerId,
        UUID sellerId,
        long amountMinor,
        String currency,
        int termsVersion,
        String state,
        Instant deliveryDeadline,
        Instant createdAt,
        Instant updatedAt) {
}
