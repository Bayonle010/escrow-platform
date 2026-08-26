package io.github.bayonle010.escrow.escrow.creation.dto;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateEscrowRequest(
        @NotNull(message = "Buyer ID is required.")
        @Schema(description = "Identity service user ID for the buyer")
        UUID buyerId,

        @NotNull(message = "Seller ID is required.")
        @Schema(description = "Identity service user ID for the seller")
        UUID sellerId,

        @NotNull(message = "Creator ID is required.")
        @Schema(description = "Participant creating the escrow")
        UUID createdBy,

        @Positive(message = "Amount must be greater than zero.")
        @Schema(description = "Amount in the currency's minor units", example = "100000")
        long amountMinor,

        @NotBlank(message = "Currency is required.")
        @Pattern(regexp = "^[A-Za-z]{3}$", message = "Currency must be a three-letter code.")
        @Schema(description = "ISO 4217 currency code", example = "NGN")
        String currency,

        @NotBlank(message = "Description is required.")
        @Size(max = 2000, message = "Description must be at most 2000 characters.")
        @Schema(description = "Description of the transaction")
        String description,

        @NotBlank(message = "Category is required.")
        @Size(max = 100, message = "Category must be at most 100 characters.")
        @Schema(description = "Transaction category", example = "GOODS")
        String category,

        @NotNull(message = "Delivery deadline is required.")
        @Future(message = "Delivery deadline must be in the future.")
        @Schema(description = "Agreed delivery deadline in UTC")
        Instant deliveryDeadline,

        @Positive(message = "Inspection period must be greater than zero days.")
        @Schema(description = "Buyer inspection period in whole days", example = "7")
        int inspectionPeriodDays,

        @NotBlank(message = "Release conditions are required.")
        @Size(max = 2000, message = "Release conditions must be at most 2000 characters.")
        String releaseConditions,

        @NotBlank(message = "Refund conditions are required.")
        @Size(max = 2000, message = "Refund conditions must be at most 2000 characters.")
        String refundConditions) {
}
