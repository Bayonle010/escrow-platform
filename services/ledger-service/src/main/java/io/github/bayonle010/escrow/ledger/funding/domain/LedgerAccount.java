package io.github.bayonle010.escrow.ledger.funding.domain;

import java.util.UUID;

public record LedgerAccount(
        UUID accountId,
        String ownerType,
        String ownerReference,
        String accountType,
        AccountSide normalSide,
        String currency,
        String status) {
}
