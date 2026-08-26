package io.github.bayonle010.escrow.escrow.creation.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import io.github.bayonle010.escrow.escrow.creation.domain.CreatedEscrow;
import io.github.bayonle010.escrow.escrow.creation.domain.EscrowCreation;
import io.github.bayonle010.escrow.escrow.creation.domain.EscrowState;
import io.github.bayonle010.escrow.escrow.creation.dto.CreateEscrowRequest;
import io.github.bayonle010.escrow.escrow.shared.api.ErrorCode;
import io.github.bayonle010.escrow.escrow.shared.exception.InvalidEscrowException;

@Service
public class EscrowCreationService {

    private static final int INITIAL_TERMS_VERSION = 1;

    private final EscrowCreationPersistenceService persistenceService;
    private final Clock clock;
    private final Set<String> supportedCurrencies;

    public EscrowCreationService(
            EscrowCreationPersistenceService persistenceService,
            Clock clock,
            Set<String> supportedCurrencies) {
        this.persistenceService = persistenceService;
        this.clock = clock;
        this.supportedCurrencies = Set.copyOf(supportedCurrencies);
    }

    public CreatedEscrow create(CreateEscrowRequest request, UUID correlationId) {
        validateParticipants(request.buyerId(), request.sellerId(), request.createdBy());
        validateAmount(request.amountMinor());

        String currency = request.currency().strip().toUpperCase(Locale.ROOT);
        validateCurrency(currency);

        Instant createdAt = clock.instant();
        validateDeliveryDeadline(request.deliveryDeadline(), createdAt);

        EscrowCreation creation = new EscrowCreation(
                request.buyerId(),
                request.sellerId(),
                request.createdBy(),
                request.amountMinor(),
                currency,
                request.description().strip(),
                request.category().strip().toUpperCase(Locale.ROOT),
                request.deliveryDeadline(),
                request.inspectionPeriodDays(),
                request.releaseConditions().strip(),
                request.refundConditions().strip(),
                INITIAL_TERMS_VERSION,
                EscrowState.AWAITING_COUNTERPARTY,
                createdAt,
                correlationId);

        UUID escrowId = persistenceService.save(creation);
        return new CreatedEscrow(
                escrowId,
                creation.buyerId(),
                creation.sellerId(),
                creation.amountMinor(),
                creation.currency(),
                creation.termsVersion(),
                creation.state(),
                creation.createdAt());
    }

    private void validateParticipants(UUID buyerId, UUID sellerId, UUID createdBy) {
        if (buyerId.equals(sellerId)) {
            throw new InvalidEscrowException(
                    ErrorCode.ESCROW_PARTICIPANTS_MUST_DIFFER,
                    "sellerId",
                    "Buyer and seller must be different users.");
        }
        if (!createdBy.equals(buyerId) && !createdBy.equals(sellerId)) {
            throw new InvalidEscrowException(
                    ErrorCode.ESCROW_CREATOR_NOT_PARTICIPANT,
                    "createdBy",
                    "Escrow creator must be the buyer or seller.");
        }
    }

    private void validateAmount(long amountMinor) {
        if (amountMinor <= 0) {
            throw new InvalidEscrowException(
                    ErrorCode.ESCROW_AMOUNT_INVALID,
                    "amountMinor",
                    "Amount must be greater than zero.");
        }
    }

    private void validateCurrency(String currency) {
        if (!supportedCurrencies.contains(currency)) {
            throw new InvalidEscrowException(
                    ErrorCode.ESCROW_UNSUPPORTED_CURRENCY,
                    "currency",
                    "Currency is not supported.");
        }
    }

    private void validateDeliveryDeadline(Instant deliveryDeadline, Instant createdAt) {
        if (!deliveryDeadline.isAfter(createdAt)) {
            throw new InvalidEscrowException(
                    ErrorCode.ESCROW_DELIVERY_DEADLINE_INVALID,
                    "deliveryDeadline",
                    "Delivery deadline must be in the future.");
        }
    }
}
