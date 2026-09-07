package io.github.bayonle010.escrow.escrow.funding.domain;

import java.time.Instant;
import java.util.UUID;

public record EscrowFundingSecuredEvent(
        UUID eventId,
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
