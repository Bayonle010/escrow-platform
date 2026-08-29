package io.github.bayonle010.escrow.ledger.funding.dto;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import io.github.bayonle010.escrow.ledger.funding.domain.PaymentSucceededEvent;

public record PaymentSucceededEventRequest(
        @NotNull(message = "Event ID is required.") UUID eventId,
        @Min(value = 1, message = "Event version must be positive.") int eventVersion,
        @NotNull(message = "Event occurrence time is required.") Instant occurredAt,
        @NotNull(message = "Payment ID is required.") UUID paymentId,
        @NotNull(message = "Escrow ID is required.") UUID escrowId,
        @NotNull(message = "Payer ID is required.") UUID payerId,
        @Positive(message = "Amount must be greater than zero.") long amountMinor,
        @NotBlank(message = "Currency is required.")
        @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a three-letter uppercase code.")
        String currency,
        @NotBlank(message = "Provider is required.")
        @Size(max = 40, message = "Provider must not exceed 40 characters.")
        String provider,
        @NotBlank(message = "Provider reference is required.")
        @Size(max = 200, message = "Provider reference must not exceed 200 characters.")
        String providerReference,
        @PositiveOrZero(message = "Aggregate version must not be negative.") long aggregateVersion,
        @NotNull(message = "Correlation ID is required.") UUID correlationId) {

    public PaymentSucceededEvent toDomain() {
        return new PaymentSucceededEvent(
                eventId,
                eventVersion,
                occurredAt,
                paymentId,
                escrowId,
                payerId,
                amountMinor,
                currency,
                provider,
                providerReference,
                aggregateVersion,
                correlationId);
    }
}
