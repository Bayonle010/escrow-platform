package io.github.bayonle010.escrow.escrow.creation.dto;

import java.time.Instant;
import java.util.UUID;

import io.github.bayonle010.escrow.escrow.creation.domain.CreatedEscrow;
import io.github.bayonle010.escrow.escrow.creation.domain.EscrowState;

public record CreatedEscrowResponse(
        UUID id,
        UUID buyerId,
        UUID sellerId,
        long amountMinor,
        String currency,
        int termsVersion,
        EscrowState state,
        Instant createdAt) {

    public static CreatedEscrowResponse from(CreatedEscrow escrow) {
        return new CreatedEscrowResponse(
                escrow.escrowId(),
                escrow.buyerId(),
                escrow.sellerId(),
                escrow.amountMinor(),
                escrow.currency(),
                escrow.termsVersion(),
                escrow.state(),
                escrow.createdAt());
    }
}
