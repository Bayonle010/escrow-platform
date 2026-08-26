package io.github.bayonle010.escrow.escrow.acceptance.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.bayonle010.escrow.escrow.acceptance.builder.EscrowTermsAcceptanceEventBuilder;
import io.github.bayonle010.escrow.escrow.acceptance.domain.AcceptedEscrowTerms;
import io.github.bayonle010.escrow.escrow.acceptance.dto.AcceptEscrowTermsRequest;
import io.github.bayonle010.escrow.escrow.acceptance.entity.EscrowTermsAcceptanceEntity;
import io.github.bayonle010.escrow.escrow.acceptance.repository.EscrowTermsAcceptanceRepository;
import io.github.bayonle010.escrow.escrow.creation.domain.EscrowState;
import io.github.bayonle010.escrow.escrow.creation.entity.EscrowEntity;
import io.github.bayonle010.escrow.escrow.creation.entity.EscrowTermsEntity;
import io.github.bayonle010.escrow.escrow.creation.repository.EscrowRepository;
import io.github.bayonle010.escrow.escrow.creation.repository.EscrowTermsRepository;
import io.github.bayonle010.escrow.escrow.creation.repository.OutboxEventRepository;
import io.github.bayonle010.escrow.escrow.shared.api.ErrorCode;
import io.github.bayonle010.escrow.escrow.shared.exception.EscrowNotFoundException;
import io.github.bayonle010.escrow.escrow.shared.exception.InvalidEscrowException;

@Service
public class EscrowTermsAcceptanceService {

    private final EscrowRepository escrowRepository;
    private final EscrowTermsRepository termsRepository;
    private final EscrowTermsAcceptanceRepository acceptanceRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final EscrowTermsAcceptanceEventBuilder eventBuilder;
    private final Clock clock;

    public EscrowTermsAcceptanceService(
            EscrowRepository escrowRepository,
            EscrowTermsRepository termsRepository,
            EscrowTermsAcceptanceRepository acceptanceRepository,
            OutboxEventRepository outboxEventRepository,
            EscrowTermsAcceptanceEventBuilder eventBuilder,
            Clock clock) {
        this.escrowRepository = escrowRepository;
        this.termsRepository = termsRepository;
        this.acceptanceRepository = acceptanceRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.eventBuilder = eventBuilder;
        this.clock = clock;
    }

    @Transactional
    public AcceptedEscrowTerms accept(
            UUID escrowId,
            AcceptEscrowTermsRequest request,
            UUID correlationId) {
        EscrowEntity escrow = escrowRepository.findById(escrowId)
                .orElseThrow(() -> new EscrowNotFoundException(escrowId));
        validateState(escrow);
        validateTermsVersion(escrow, request.termsVersion());

        EscrowTermsEntity terms = termsRepository
                .findByEscrowIdAndTermsVersion(escrowId, request.termsVersion())
                .orElseThrow(() -> new IllegalStateException(
                        "Current terms are missing for escrow " + escrowId + "."));
        validateCounterparty(escrow, terms, request.participantId());

        Instant acceptedAt = clock.instant();
        validateNotExpired(escrow, acceptedAt);

        escrow.acceptTerms(acceptedAt);
        escrowRepository.saveAndFlush(escrow);

        EscrowTermsAcceptanceEntity acceptance = acceptanceRepository.save(
                EscrowTermsAcceptanceEntity.builder()
                        .escrowId(escrowId)
                        .termsVersion(request.termsVersion())
                        .participantId(request.participantId())
                        .acceptedAt(acceptedAt)
                        .build());
        outboxEventRepository.save(eventBuilder.build(
                escrowId,
                request.participantId(),
                request.termsVersion(),
                escrow.getVersion() + 1,
                acceptedAt,
                correlationId));
        outboxEventRepository.flush();

        return new AcceptedEscrowTerms(
                escrowId,
                acceptance.getAcceptanceReference(),
                request.termsVersion(),
                request.participantId(),
                acceptedAt,
                escrow.getState());
    }

    private void validateState(EscrowEntity escrow) {
        if (escrow.getState() != EscrowState.AWAITING_COUNTERPARTY) {
            throw new InvalidEscrowException(
                    ErrorCode.ESCROW_STATE_INVALID,
                    "state",
                    "Terms cannot be accepted while the escrow is in state "
                            + escrow.getState() + ".");
        }
    }

    private void validateTermsVersion(EscrowEntity escrow, int requestedVersion) {
        if (requestedVersion != escrow.getCurrentTermsVersion()) {
            throw new InvalidEscrowException(
                    ErrorCode.ESCROW_TERMS_VERSION_MISMATCH,
                    "termsVersion",
                    "Terms version " + requestedVersion + " is not the current version.");
        }
    }

    private void validateCounterparty(
            EscrowEntity escrow,
            EscrowTermsEntity terms,
            UUID participantId) {
        UUID counterpartyId = terms.getCreatedBy().equals(escrow.getBuyerId())
                ? escrow.getSellerId()
                : escrow.getBuyerId();
        if (!counterpartyId.equals(participantId)) {
            throw new InvalidEscrowException(
                    ErrorCode.ESCROW_ACCEPTOR_NOT_COUNTERPARTY,
                    "participantId",
                    "Only the invited counterparty can accept these terms.");
        }
    }

    private void validateNotExpired(EscrowEntity escrow, Instant acceptedAt) {
        if (!escrow.getDeliveryDeadline().isAfter(acceptedAt)) {
            throw new InvalidEscrowException(
                    ErrorCode.ESCROW_TERMS_EXPIRED,
                    "escrowId",
                    "Terms cannot be accepted after the delivery deadline.");
        }
    }
}
