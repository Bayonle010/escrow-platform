package io.github.bayonle010.escrow.ledger.funding.dto;

import java.time.Instant;
import java.util.UUID;

import io.github.bayonle010.escrow.ledger.funding.domain.AccountBalance;

public record AccountBalanceResponse(
        UUID accountId,
        String ownerType,
        String ownerReference,
        String accountType,
        String currency,
        long postedBalanceMinor,
        long availableBalanceMinor,
        long version,
        Instant updatedAt) {

    public static AccountBalanceResponse from(AccountBalance balance) {
        return new AccountBalanceResponse(
                balance.accountId(),
                balance.ownerType(),
                balance.ownerReference(),
                balance.accountType(),
                balance.currency(),
                balance.postedBalanceMinor(),
                balance.availableBalanceMinor(),
                balance.version(),
                balance.updatedAt());
    }
}
