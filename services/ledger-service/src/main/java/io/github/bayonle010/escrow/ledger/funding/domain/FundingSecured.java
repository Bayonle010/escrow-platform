package io.github.bayonle010.escrow.ledger.funding.domain;

import java.time.Instant;
import java.util.UUID;

public record FundingSecured(
        UUID journalId,
        UUID paymentId,
        UUID escrowId,
        long amountMinor,
        String currency,
        UUID providerClearingAccountId,
        UUID escrowHeldAccountId,
        Instant securedAt,
        boolean replayed) {
}
