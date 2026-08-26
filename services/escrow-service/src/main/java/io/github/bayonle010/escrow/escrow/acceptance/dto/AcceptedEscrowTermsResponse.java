package io.github.bayonle010.escrow.escrow.acceptance.dto;

import java.time.Instant;
import java.util.UUID;

import io.github.bayonle010.escrow.escrow.acceptance.domain.AcceptedEscrowTerms;
import io.github.bayonle010.escrow.escrow.creation.domain.EscrowState;

public record AcceptedEscrowTermsResponse(
        UUID escrowId,
        UUID acceptanceReference,
        int termsVersion,
        UUID acceptedBy,
        Instant acceptedAt,
        EscrowState state) {

    public static AcceptedEscrowTermsResponse from(AcceptedEscrowTerms acceptance) {
        return new AcceptedEscrowTermsResponse(
                acceptance.escrowId(),
                acceptance.acceptanceReference(),
                acceptance.termsVersion(),
                acceptance.acceptedBy(),
                acceptance.acceptedAt(),
                acceptance.state());
    }
}
