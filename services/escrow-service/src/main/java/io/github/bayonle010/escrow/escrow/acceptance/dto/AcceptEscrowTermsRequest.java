package io.github.bayonle010.escrow.escrow.acceptance.dto;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AcceptEscrowTermsRequest(
        @NotNull(message = "Participant ID is required.")
        @Schema(description = "Identity service user ID for the counterparty")
        UUID participantId,

        @Positive(message = "Terms version must be greater than zero.")
        @Schema(description = "Exact terms version being accepted", example = "1")
        int termsVersion) {
}
