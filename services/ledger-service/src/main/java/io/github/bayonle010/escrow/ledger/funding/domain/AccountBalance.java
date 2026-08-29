package io.github.bayonle010.escrow.ledger.funding.domain;

import java.time.Instant;
import java.util.UUID;

public record AccountBalance(
        UUID accountId,
        String ownerType,
        String ownerReference,
        String accountType,
        String currency,
        long postedBalanceMinor,
        long availableBalanceMinor,
        long version,
        Instant updatedAt) {
}
