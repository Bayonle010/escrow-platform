package io.github.bayonle010.escrow.escrow.acceptance.event;

import java.time.Instant;
import java.util.UUID;

import io.github.bayonle010.escrow.escrow.creation.domain.EscrowState;

public record EscrowTermsAcceptedPayload(
        String eventType,
        int eventVersion,
        Instant occurredAt,
        UUID escrowId,
        UUID participantId,
        int termsVersion,
        EscrowState state,
        long aggregateVersion) {
}
