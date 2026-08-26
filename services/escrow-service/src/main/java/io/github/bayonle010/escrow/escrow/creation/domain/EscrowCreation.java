package io.github.bayonle010.escrow.escrow.creation.domain;

import java.time.Instant;
import java.util.UUID;

public record EscrowCreation(
        UUID buyerId,
        UUID sellerId,
        UUID createdBy,
        long amountMinor,
        String currency,
        String description,
        String category,
        Instant deliveryDeadline,
        int inspectionPeriodDays,
        String releaseConditions,
        String refundConditions,
        int termsVersion,
        EscrowState state,
        Instant createdAt,
        UUID correlationId) {
}
