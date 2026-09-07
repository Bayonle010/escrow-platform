package io.github.bayonle010.escrow.escrow.funding.event;

import java.time.Instant;
import java.util.UUID;

import io.github.bayonle010.escrow.escrow.creation.domain.EscrowState;

public record EscrowFundedPayload(
        String eventType,
        int eventVersion,
        Instant occurredAt,
        UUID escrowId,
        UUID journalId,
        UUID paymentId,
        long amountMinor,
        String currency,
        EscrowState state,
        long aggregateVersion,
        UUID correlationId,
        UUID causationId) {
}
