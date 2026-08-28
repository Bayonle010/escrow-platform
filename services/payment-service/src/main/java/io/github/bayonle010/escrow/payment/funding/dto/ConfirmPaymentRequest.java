package io.github.bayonle010.escrow.payment.funding.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfirmPaymentRequest(
        @NotBlank(message = "Provider reference is required.")
        @Size(max = 200, message = "Provider reference must not exceed 200 characters.")
        String providerReference) {
}
