package io.github.bayonle010.escrow.escrow.query.domain;

import java.time.Instant;
import java.util.UUID;

import io.github.bayonle010.escrow.escrow.creation.domain.EscrowState;

public record EscrowDetails(
        UUID escrowId,
        UUID buyerId,
        UUID sellerId,
        long amountMinor,
        String currency,
        int termsVersion,
        EscrowState state,
        Instant deliveryDeadline,
        Instant createdAt,
        Instant updatedAt) {
}
