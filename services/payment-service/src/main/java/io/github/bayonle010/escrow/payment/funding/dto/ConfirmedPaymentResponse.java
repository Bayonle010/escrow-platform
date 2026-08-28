package io.github.bayonle010.escrow.payment.funding.dto;

import java.time.Instant;
import java.util.UUID;

import io.github.bayonle010.escrow.payment.funding.domain.ConfirmedPayment;
import io.github.bayonle010.escrow.payment.funding.domain.PaymentStatus;

public record ConfirmedPaymentResponse(
        UUID id,
        UUID escrowId,
        UUID payerId,
        long amountMinor,
        String currency,
        String provider,
        String providerReference,
        PaymentStatus status,
        Instant confirmedAt,
        boolean replayed) {

    public static ConfirmedPaymentResponse from(ConfirmedPayment payment) {
        return new ConfirmedPaymentResponse(
                payment.paymentId(),
                payment.escrowId(),
                payment.payerId(),
                payment.amountMinor(),
                payment.currency(),
                payment.provider(),
                payment.providerReference(),
                payment.status(),
                payment.confirmedAt(),
                payment.replayed());
    }
}
