package io.github.bayonle010.escrow.payment.funding.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import io.github.bayonle010.escrow.payment.funding.domain.EscrowFundingSnapshot;
import io.github.bayonle010.escrow.payment.funding.domain.InitiatedPayment;
import io.github.bayonle010.escrow.payment.funding.entity.PaymentEntity;
import io.github.bayonle010.escrow.payment.funding.gateway.EscrowFundingSnapshotGateway;
import io.github.bayonle010.escrow.payment.funding.repository.PaymentRepository;
import io.github.bayonle010.escrow.payment.shared.api.ErrorCode;
import io.github.bayonle010.escrow.payment.shared.exception.PaymentApiException;

@Service
public class FundingInitiationService {

    private final PaymentRepository paymentRepository;
    private final EscrowFundingSnapshotGateway escrowGateway;
    private final PaymentPersistenceService persistenceService;
    private final RequestFingerprint requestFingerprint;
    private final Clock clock;

    public FundingInitiationService(
            PaymentRepository paymentRepository,
            EscrowFundingSnapshotGateway escrowGateway,
            PaymentPersistenceService persistenceService,
            RequestFingerprint requestFingerprint,
            Clock clock) {
        this.paymentRepository = paymentRepository;
        this.escrowGateway = escrowGateway;
        this.persistenceService = persistenceService;
        this.requestFingerprint = requestFingerprint;
        this.clock = clock;
    }

    public InitiatedPayment initiate(
            UUID escrowId,
            UUID payerId,
            String rawIdempotencyKey,
            UUID correlationId) {
        String idempotencyKey = validateIdempotencyKey(rawIdempotencyKey);
        String fingerprint = requestFingerprint.create(escrowId, payerId);

        Optional<PaymentEntity> replay = paymentRepository
                .findByPayerIdAndIdempotencyKey(payerId, idempotencyKey);
        if (replay.isPresent()) {
            return replay(replay.get(), fingerprint);
        }

        EscrowFundingSnapshot escrow = escrowGateway.get(escrowId, correlationId);
        Instant initiatedAt = clock.instant();
        validateEscrow(escrow, escrowId, payerId, initiatedAt);

        paymentRepository.findByEscrowId(escrowId).ifPresent(existing -> {
            throw paymentAlreadyExists(escrowId);
        });

        try {
            return toDomain(persistenceService.create(
                    escrow,
                    payerId,
                    idempotencyKey,
                    fingerprint,
                    initiatedAt,
                    correlationId), false);
        } catch (DataIntegrityViolationException exception) {
            return resolveConcurrentRequest(escrowId, payerId, idempotencyKey, fingerprint, exception);
        }
    }

    private String validateIdempotencyKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            throw new PaymentApiException(
                    ErrorCode.IDEMPOTENCY_KEY_REQUIRED,
                    HttpStatus.BAD_REQUEST,
                    "Idempotency-Key",
                    "Idempotency-Key is required for funding initiation.");
        }
        String key = rawKey.trim();
        try {
            UUID parsedKey = UUID.fromString(key);
            boolean canonical = parsedKey.toString().equalsIgnoreCase(key);
            boolean supportedVersion = parsedKey.version() == 4 || parsedKey.version() == 7;
            if (!canonical || !supportedVersion) {
                throw invalidIdempotencyKey();
            }
            return parsedKey.toString();
        } catch (IllegalArgumentException exception) {
            throw invalidIdempotencyKey();
        }
    }

    private PaymentApiException invalidIdempotencyKey() {
        return new PaymentApiException(
                ErrorCode.IDEMPOTENCY_KEY_INVALID,
                HttpStatus.BAD_REQUEST,
                "Idempotency-Key",
                "Idempotency-Key must be a canonical UUID version 4 or version 7.");
    }

    private void validateEscrow(
            EscrowFundingSnapshot escrow,
            UUID requestedEscrowId,
            UUID payerId,
            Instant initiatedAt) {
        if (!requestedEscrowId.equals(escrow.id())) {
            throw new IllegalStateException("Escrow service returned a mismatched escrow ID.");
        }
        if (!payerId.equals(escrow.buyerId())) {
            throw new PaymentApiException(
                    ErrorCode.PAYER_NOT_BUYER,
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "payerId",
                    "Only the escrow buyer can initiate funding.");
        }
        if (!"AWAITING_FUNDING".equals(escrow.state())) {
            throw new PaymentApiException(
                    ErrorCode.ESCROW_STATE_INVALID,
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "escrowId",
                    "Funding cannot be initiated while the escrow is in state " + escrow.state() + ".");
        }
        if (!escrow.deliveryDeadline().isAfter(initiatedAt)) {
            throw new PaymentApiException(
                    ErrorCode.ESCROW_TERMS_EXPIRED,
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "escrowId",
                    "Funding cannot be initiated after the delivery deadline.");
        }
    }

    private InitiatedPayment resolveConcurrentRequest(
            UUID escrowId,
            UUID payerId,
            String idempotencyKey,
            String fingerprint,
            DataIntegrityViolationException originalException) {
        Optional<PaymentEntity> keyedPayment = paymentRepository
                .findByPayerIdAndIdempotencyKey(payerId, idempotencyKey);
        if (keyedPayment.isPresent()) {
            return replay(keyedPayment.get(), fingerprint);
        }
        if (paymentRepository.findByEscrowId(escrowId).isPresent()) {
            throw paymentAlreadyExists(escrowId);
        }
        throw originalException;
    }

    private InitiatedPayment replay(PaymentEntity payment, String fingerprint) {
        if (!payment.getRequestFingerprint().equals(fingerprint)) {
            throw new PaymentApiException(
                    ErrorCode.IDEMPOTENCY_KEY_REUSED,
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "Idempotency-Key",
                    "The idempotency key was already used for a different funding request.");
        }
        return toDomain(payment, true);
    }

    private PaymentApiException paymentAlreadyExists(UUID escrowId) {
        return new PaymentApiException(
                ErrorCode.PAYMENT_ALREADY_EXISTS,
                HttpStatus.CONFLICT,
                "escrowId",
                "A funding instruction already exists for escrow " + escrowId + ".");
    }

    static InitiatedPayment toDomain(PaymentEntity payment, boolean replayed) {
        return new InitiatedPayment(
                payment.getPaymentId(),
                payment.getEscrowId(),
                payment.getPayerId(),
                payment.getAmountMinor(),
                payment.getCurrency(),
                payment.getProvider(),
                payment.getStatus(),
                payment.getCreatedAt(),
                replayed);
    }
}
