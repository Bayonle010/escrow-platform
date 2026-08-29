package io.github.bayonle010.escrow.ledger.funding.dto;

import java.time.Instant;
import java.util.UUID;

import io.github.bayonle010.escrow.ledger.funding.domain.FundingSecured;

public record FundingSecuredResponse(
        UUID journalId,
        UUID paymentId,
        UUID escrowId,
        long amountMinor,
        String currency,
        UUID providerClearingAccountId,
        UUID escrowHeldAccountId,
        Instant securedAt,
        boolean replayed) {

    public static FundingSecuredResponse from(FundingSecured funding) {
        return new FundingSecuredResponse(
                funding.journalId(),
                funding.paymentId(),
                funding.escrowId(),
                funding.amountMinor(),
                funding.currency(),
                funding.providerClearingAccountId(),
                funding.escrowHeldAccountId(),
                funding.securedAt(),
                funding.replayed());
    }
}
