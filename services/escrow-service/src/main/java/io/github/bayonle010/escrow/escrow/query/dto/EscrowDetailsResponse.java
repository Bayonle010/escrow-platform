package io.github.bayonle010.escrow.escrow.query.dto;

import java.time.Instant;
import java.util.UUID;

import io.github.bayonle010.escrow.escrow.creation.domain.EscrowState;
import io.github.bayonle010.escrow.escrow.query.domain.EscrowDetails;

public record EscrowDetailsResponse(
        UUID id,
        UUID buyerId,
        UUID sellerId,
        long amountMinor,
        String currency,
        int termsVersion,
        EscrowState state,
        Instant deliveryDeadline,
        Instant createdAt,
        Instant updatedAt) {

    public static EscrowDetailsResponse from(EscrowDetails escrow) {
        return new EscrowDetailsResponse(
                escrow.escrowId(),
                escrow.buyerId(),
                escrow.sellerId(),
                escrow.amountMinor(),
                escrow.currency(),
                escrow.termsVersion(),
                escrow.state(),
                escrow.deliveryDeadline(),
                escrow.createdAt(),
                escrow.updatedAt());
    }
}
