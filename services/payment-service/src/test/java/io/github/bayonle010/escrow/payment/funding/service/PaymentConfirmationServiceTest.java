package io.github.bayonle010.escrow.payment.funding.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import io.github.bayonle010.escrow.payment.funding.builder.PaymentSucceededEventBuilder;
import io.github.bayonle010.escrow.payment.funding.domain.PaymentStatus;
import io.github.bayonle010.escrow.payment.funding.entity.OutboxEventEntity;
import io.github.bayonle010.escrow.payment.funding.entity.PaymentEntity;
import io.github.bayonle010.escrow.payment.funding.repository.OutboxEventRepository;
import io.github.bayonle010.escrow.payment.funding.repository.PaymentRepository;
import io.github.bayonle010.escrow.payment.shared.api.ErrorCode;
import io.github.bayonle010.escrow.payment.shared.exception.PaymentApiException;

class PaymentConfirmationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-28T00:00:00Z");
    private static final UUID PAYMENT_ID = UUID.fromString("019c0000-0000-7000-8000-000000000030");
    private static final UUID OTHER_PAYMENT_ID = UUID.fromString("019c0000-0000-7000-8000-000000000031");
    private static final UUID ESCROW_ID = UUID.fromString("019c0000-0000-7000-8000-000000000020");
    private static final UUID BUYER_ID = UUID.fromString("019c0000-0000-7000-8000-000000000001");
    private static final UUID CORRELATION_ID = UUID.fromString("019c0000-0000-7000-8000-000000000010");
    private static final String PROVIDER_REFERENCE = "simulated-transaction-1001";

    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);
    private final PaymentSucceededEventBuilder eventBuilder = mock(PaymentSucceededEventBuilder.class);
    private final PaymentConfirmationService service = new PaymentConfirmationService(
            paymentRepository,
            outboxEventRepository,
            eventBuilder,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void commitsSucceededPaymentAndOutboxEvent() {
        PaymentEntity payment = payment(PAYMENT_ID, PaymentStatus.PROCESSING, null);
        OutboxEventEntity event = mock(OutboxEventEntity.class);
        when(paymentRepository.findByIdForUpdate(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(paymentRepository.findByProviderAndProviderReference("SIMULATED", PROVIDER_REFERENCE))
                .thenReturn(Optional.empty());
        when(eventBuilder.build(payment, CORRELATION_ID)).thenReturn(event);

        var result = service.confirm(PAYMENT_ID, "  " + PROVIDER_REFERENCE + "  ", CORRELATION_ID);

        assertThat(result.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(result.providerReference()).isEqualTo(PROVIDER_REFERENCE);
        assertThat(result.confirmedAt()).isEqualTo(NOW);
        assertThat(result.replayed()).isFalse();
        verify(paymentRepository).saveAndFlush(payment);
        verify(outboxEventRepository).save(event);
        verify(outboxEventRepository).flush();
    }

    @Test
    void safelyReplaysTheSameProviderConfirmation() {
        PaymentEntity payment = payment(PAYMENT_ID, PaymentStatus.SUCCEEDED, PROVIDER_REFERENCE);
        when(paymentRepository.findByIdForUpdate(PAYMENT_ID)).thenReturn(Optional.of(payment));

        var result = service.confirm(PAYMENT_ID, PROVIDER_REFERENCE, CORRELATION_ID);

        assertThat(result.replayed()).isTrue();
        assertThat(result.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        verifyNoInteractions(outboxEventRepository, eventBuilder);
    }

    @Test
    void rejectsASecondReferenceForAnAlreadySucceededPayment() {
        PaymentEntity payment = payment(PAYMENT_ID, PaymentStatus.SUCCEEDED, PROVIDER_REFERENCE);
        when(paymentRepository.findByIdForUpdate(PAYMENT_ID)).thenReturn(Optional.of(payment));

        assertError(
                () -> service.confirm(PAYMENT_ID, "another-reference", CORRELATION_ID),
                ErrorCode.PAYMENT_OUTCOME_CONFLICT);
        verifyNoInteractions(outboxEventRepository, eventBuilder);
    }

    @Test
    void rejectsConfirmationOfAFinalFailedPayment() {
        PaymentEntity payment = payment(PAYMENT_ID, PaymentStatus.FAILED, null);
        when(paymentRepository.findByIdForUpdate(PAYMENT_ID)).thenReturn(Optional.of(payment));

        assertError(
                () -> service.confirm(PAYMENT_ID, PROVIDER_REFERENCE, CORRELATION_ID),
                ErrorCode.PAYMENT_OUTCOME_CONFLICT);
        verifyNoInteractions(outboxEventRepository, eventBuilder);
    }

    @Test
    void rejectsAProviderReferenceAlreadyUsedByAnotherPayment() {
        PaymentEntity payment = payment(PAYMENT_ID, PaymentStatus.PROCESSING, null);
        PaymentEntity existing = payment(OTHER_PAYMENT_ID, PaymentStatus.SUCCEEDED, PROVIDER_REFERENCE);
        when(paymentRepository.findByIdForUpdate(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(paymentRepository.findByProviderAndProviderReference("SIMULATED", PROVIDER_REFERENCE))
                .thenReturn(Optional.of(existing));

        assertError(
                () -> service.confirm(PAYMENT_ID, PROVIDER_REFERENCE, CORRELATION_ID),
                ErrorCode.PROVIDER_REFERENCE_REUSED);
        verifyNoInteractions(outboxEventRepository, eventBuilder);
    }

    @Test
    void translatesAConcurrentProviderReferenceConstraintViolation() {
        PaymentEntity payment = payment(PAYMENT_ID, PaymentStatus.PROCESSING, null);
        when(paymentRepository.findByIdForUpdate(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(paymentRepository.findByProviderAndProviderReference("SIMULATED", PROVIDER_REFERENCE))
                .thenReturn(Optional.empty());
        when(paymentRepository.saveAndFlush(payment))
                .thenThrow(new DataIntegrityViolationException("duplicate provider reference"));

        assertError(
                () -> service.confirm(PAYMENT_ID, PROVIDER_REFERENCE, CORRELATION_ID),
                ErrorCode.PROVIDER_REFERENCE_REUSED);
        verifyNoInteractions(outboxEventRepository, eventBuilder);
    }

    @Test
    void reportsAMissingPayment() {
        when(paymentRepository.findByIdForUpdate(PAYMENT_ID)).thenReturn(Optional.empty());

        assertError(
                () -> service.confirm(PAYMENT_ID, PROVIDER_REFERENCE, CORRELATION_ID),
                ErrorCode.PAYMENT_NOT_FOUND);
    }

    private PaymentEntity payment(
            UUID paymentId,
            PaymentStatus status,
            String providerReference) {
        return PaymentEntity.builder()
                .paymentId(paymentId)
                .escrowId(ESCROW_ID)
                .payerId(BUYER_ID)
                .amountMinor(100000)
                .currency("NGN")
                .provider("SIMULATED")
                .providerReference(providerReference)
                .status(status)
                .idempotencyKey("8e03978e-40d5-43e8-bc93-6894a57f9324")
                .requestFingerprint("a".repeat(64))
                .createdAt(NOW.minusSeconds(60))
                .updatedAt(NOW)
                .build();
    }

    private void assertError(Runnable action, ErrorCode expectedCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        PaymentApiException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(expectedCode));
    }
}
