package io.github.bayonle010.escrow.ledger.funding.domain;

import java.time.Instant;
import java.util.UUID;

public record LedgerEntry(
        UUID entryId,
        UUID journalId,
        UUID accountId,
        AccountSide direction,
        long amountMinor,
        String currency,
        int sequence,
        Instant createdAt) {
}
