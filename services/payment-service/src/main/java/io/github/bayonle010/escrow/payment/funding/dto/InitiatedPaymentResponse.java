package io.github.bayonle010.escrow.payment.funding.dto;

import java.time.Instant;
import java.util.UUID;

import io.github.bayonle010.escrow.payment.funding.domain.InitiatedPayment;
import io.github.bayonle010.escrow.payment.funding.domain.PaymentStatus;

public record InitiatedPaymentResponse(
        UUID id,
        UUID escrowId,
        UUID payerId,
        long amountMinor,
        String currency,
        String provider,
        PaymentStatus status,
        Instant createdAt,
        boolean replayed) {

    public static InitiatedPaymentResponse from(InitiatedPayment payment) {
        return new InitiatedPaymentResponse(
                payment.paymentId(),
                payment.escrowId(),
                payment.payerId(),
                payment.amountMinor(),
                payment.currency(),
                payment.provider(),
                payment.status(),
                payment.createdAt(),
                payment.replayed());
    }
}
