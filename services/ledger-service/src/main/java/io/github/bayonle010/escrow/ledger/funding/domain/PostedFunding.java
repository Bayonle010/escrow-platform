package io.github.bayonle010.escrow.ledger.funding.domain;

import java.time.Instant;
import java.util.UUID;

public record PostedFunding(
        UUID journalId,
        UUID paymentId,
        UUID escrowId,
        long amountMinor,
        String currency,
        String provider,
        String providerReference,
        UUID providerClearingAccountId,
        UUID escrowHeldAccountId,
        Instant securedAt) {
}
