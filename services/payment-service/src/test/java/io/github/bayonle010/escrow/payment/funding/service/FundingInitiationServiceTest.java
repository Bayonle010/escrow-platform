package io.github.bayonle010.escrow.payment.funding.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import io.github.bayonle010.escrow.payment.funding.domain.EscrowFundingSnapshot;
import io.github.bayonle010.escrow.payment.funding.domain.PaymentStatus;
import io.github.bayonle010.escrow.payment.funding.entity.PaymentEntity;
import io.github.bayonle010.escrow.payment.funding.gateway.EscrowFundingSnapshotGateway;
import io.github.bayonle010.escrow.payment.funding.repository.PaymentRepository;
import io.github.bayonle010.escrow.payment.shared.api.ErrorCode;
import io.github.bayonle010.escrow.payment.shared.exception.PaymentApiException;

class FundingInitiationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-20T12:00:00Z");
    private static final UUID ESCROW_ID = UUID.fromString("019c0000-0000-7000-8000-000000000020");
    private static final UUID BUYER_ID = UUID.fromString("019c0000-0000-7000-8000-000000000001");
    private static final UUID SELLER_ID = UUID.fromString("019c0000-0000-7000-8000-000000000002");
    private static final UUID PAYMENT_ID = UUID.fromString("019c0000-0000-7000-8000-000000000030");
    private static final UUID CORRELATION_ID = UUID.fromString("019c0000-0000-7000-8000-000000000010");
    private static final String IDEMPOTENCY_KEY = "8e03978e-40d5-43e8-bc93-6894a57f9324";

    private final PaymentRepository repository = mock(PaymentRepository.class);
    private final EscrowFundingSnapshotGateway gateway = mock(EscrowFundingSnapshotGateway.class);
    private final PaymentPersistenceService persistence = mock(PaymentPersistenceService.class);
    private final RequestFingerprint fingerprint = new RequestFingerprint();
    private final FundingInitiationService service = new FundingInitiationService(
            repository,
            gateway,
            persistence,
            fingerprint,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void createsAProcessingPaymentFromTheAuthoritativeEscrowSnapshot() {
        EscrowFundingSnapshot snapshot = validSnapshot();
        when(repository.findByPayerIdAndIdempotencyKey(BUYER_ID, IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty());
        when(repository.findByEscrowId(ESCROW_ID)).thenReturn(Optional.empty());
        when(gateway.get(ESCROW_ID, CORRELATION_ID)).thenReturn(snapshot);
        when(persistence.create(
                any(), any(), any(), any(), any(), any()))
                .thenReturn(payment(IDEMPOTENCY_KEY, fingerprint.create(ESCROW_ID, BUYER_ID)));

        var result = service.initiate(ESCROW_ID, BUYER_ID, IDEMPOTENCY_KEY, CORRELATION_ID);

        assertThat(result.paymentId()).isEqualTo(PAYMENT_ID);
        assertThat(result.amountMinor()).isEqualTo(100000);
        assertThat(result.currency()).isEqualTo("NGN");
        assertThat(result.status()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(result.replayed()).isFalse();
    }

    @Test
    void safelyReplaysTheOriginalResultWithoutCallingEscrowAgain() {
        PaymentEntity existing = payment(IDEMPOTENCY_KEY, fingerprint.create(ESCROW_ID, BUYER_ID));
        when(repository.findByPayerIdAndIdempotencyKey(BUYER_ID, IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(existing));

        var result = service.initiate(ESCROW_ID, BUYER_ID, IDEMPOTENCY_KEY, CORRELATION_ID);

        assertThat(result.paymentId()).isEqualTo(PAYMENT_ID);
        assertThat(result.replayed()).isTrue();
        verifyNoInteractions(gateway, persistence);
    }

    @Test
    void rejectsIdempotencyKeyReuseForAnotherEscrow() {
        PaymentEntity existing = payment(
                IDEMPOTENCY_KEY,
                fingerprint.create(UUID.fromString("019c0000-0000-7000-8000-000000000099"), BUYER_ID));
        when(repository.findByPayerIdAndIdempotencyKey(BUYER_ID, IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.initiate(
                ESCROW_ID, BUYER_ID, IDEMPOTENCY_KEY, CORRELATION_ID))
                .isInstanceOfSatisfying(PaymentApiException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED);
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
                });
        verifyNoInteractions(gateway, persistence);
    }

    @Test
    void rejectsARequestFromAnyoneOtherThanTheBuyer() {
        when(repository.findByPayerIdAndIdempotencyKey(SELLER_ID, IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty());
        when(gateway.get(ESCROW_ID, CORRELATION_ID)).thenReturn(validSnapshot());

        assertError(
                () -> service.initiate(ESCROW_ID, SELLER_ID, IDEMPOTENCY_KEY, CORRELATION_ID),
                ErrorCode.PAYER_NOT_BUYER);
        verifyNoInteractions(persistence);
    }

    @Test
    void rejectsAnEscrowThatIsNotAwaitingFunding() {
        EscrowFundingSnapshot snapshot = new EscrowFundingSnapshot(
                ESCROW_ID, BUYER_ID, SELLER_ID, 100000, "NGN", 1,
                "FUNDING_PROCESSING", NOW.plusSeconds(86400), NOW, NOW);
        when(repository.findByPayerIdAndIdempotencyKey(BUYER_ID, IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty());
        when(gateway.get(ESCROW_ID, CORRELATION_ID)).thenReturn(snapshot);

        assertError(
                () -> service.initiate(ESCROW_ID, BUYER_ID, IDEMPOTENCY_KEY, CORRELATION_ID),
                ErrorCode.ESCROW_STATE_INVALID);
        verifyNoInteractions(persistence);
    }

    @Test
    void requiresAnIdempotencyKeyBeforeAnyExternalCall() {
        assertError(
                () -> service.initiate(ESCROW_ID, BUYER_ID, " ", CORRELATION_ID),
                ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        verifyNoInteractions(repository, gateway, persistence);
    }

    @Test
    void rejectsAOneCharacterIdempotencyKeyBeforeAnyExternalCall() {
        assertError(
                () -> service.initiate(ESCROW_ID, BUYER_ID, "a", CORRELATION_ID),
                ErrorCode.IDEMPOTENCY_KEY_INVALID);
        verifyNoInteractions(repository, gateway, persistence);
    }

    @Test
    void rejectsAUuidVersionThatIsNotFourOrSeven() {
        assertError(
                () -> service.initiate(
                        ESCROW_ID,
                        BUYER_ID,
                        "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
                        CORRELATION_ID),
                ErrorCode.IDEMPOTENCY_KEY_INVALID);
        verifyNoInteractions(repository, gateway, persistence);
    }

    private EscrowFundingSnapshot validSnapshot() {
        return new EscrowFundingSnapshot(
                ESCROW_ID,
                BUYER_ID,
                SELLER_ID,
                100000,
                "NGN",
                1,
                "AWAITING_FUNDING",
                NOW.plusSeconds(86400),
                NOW.minusSeconds(3600),
                NOW.minusSeconds(60));
    }

    private PaymentEntity payment(String idempotencyKey, String requestFingerprint) {
        return PaymentEntity.builder()
                .paymentId(PAYMENT_ID)
                .escrowId(ESCROW_ID)
                .payerId(BUYER_ID)
                .amountMinor(100000)
                .currency("NGN")
                .provider("SIMULATED")
                .status(PaymentStatus.PROCESSING)
                .idempotencyKey(idempotencyKey)
                .requestFingerprint(requestFingerprint)
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
    }

    private void assertError(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(PaymentApiException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
    }
}
