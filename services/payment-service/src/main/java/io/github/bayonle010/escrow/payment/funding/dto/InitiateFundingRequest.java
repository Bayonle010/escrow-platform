package io.github.bayonle010.escrow.payment.funding.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;

public record InitiateFundingRequest(
        @NotNull(message = "Payer ID is required.")
        @Schema(description = "Identity service user ID for the buyer")
        UUID payerId) {
}
