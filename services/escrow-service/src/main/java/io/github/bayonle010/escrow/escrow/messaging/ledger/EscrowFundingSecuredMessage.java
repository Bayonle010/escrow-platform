package io.github.bayonle010.escrow.escrow.messaging.ledger;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record EscrowFundingSecuredMessage(
        @NotBlank String eventType,
        @Positive int eventVersion,
        @NotNull Instant occurredAt,
        @NotNull UUID journalId,
        @NotNull UUID paymentId,
        @NotNull UUID escrowId,
        @Positive long amountMinor,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
        @NotNull UUID correlationId,
        @NotNull UUID causationId) {
}
