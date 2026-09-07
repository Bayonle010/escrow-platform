package io.github.bayonle010.escrow.escrow.funding.domain;

import java.time.Instant;
import java.util.UUID;

import io.github.bayonle010.escrow.escrow.creation.domain.EscrowState;

public record FundedEscrow(
        UUID escrowId,
        EscrowState state,
        Instant fundedAt,
        boolean replayed) {
}
