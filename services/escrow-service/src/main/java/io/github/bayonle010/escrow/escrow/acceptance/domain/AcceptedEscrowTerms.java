package io.github.bayonle010.escrow.escrow.acceptance.domain;

import java.time.Instant;
import java.util.UUID;

import io.github.bayonle010.escrow.escrow.creation.domain.EscrowState;

public record AcceptedEscrowTerms(
        UUID escrowId,
        UUID acceptanceReference,
        int termsVersion,
        UUID acceptedBy,
        Instant acceptedAt,
        EscrowState state) {
}
