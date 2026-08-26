package io.github.bayonle010.escrow.escrow.creation.event;

import java.time.Instant;
import java.util.UUID;

import io.github.bayonle010.escrow.escrow.creation.domain.EscrowState;

public record EscrowCreatedPayload(
        String eventType,
        int eventVersion,
        Instant occurredAt,
        UUID escrowId,
        UUID buyerId,
        UUID sellerId,
        long amountMinor,
        String currency,
        int termsVersion,
        EscrowState state,
        int aggregateVersion) {
}
