package io.github.bayonle010.escrow.ledger.funding.event;

import java.time.Instant;
import java.util.UUID;

public record EscrowFundingSecuredPayload(
        String eventType,
        int eventVersion,
        Instant occurredAt,
        UUID journalId,
        UUID paymentId,
        UUID escrowId,
        long amountMinor,
        String currency,
        UUID correlationId,
        UUID causationId) {
}
