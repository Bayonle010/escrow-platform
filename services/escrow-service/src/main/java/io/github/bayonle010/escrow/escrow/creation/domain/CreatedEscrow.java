package io.github.bayonle010.escrow.escrow.creation.domain;

import java.time.Instant;
import java.util.UUID;

public record CreatedEscrow(
        UUID escrowId,
        UUID buyerId,
        UUID sellerId,
        long amountMinor,
        String currency,
        int termsVersion,
        EscrowState state,
        Instant createdAt) {
}
