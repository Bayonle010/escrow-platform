package io.github.bayonle010.escrow.escrow.funding.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.bayonle010.escrow.escrow.creation.domain.EscrowState;
import io.github.bayonle010.escrow.escrow.creation.entity.EscrowEntity;
import io.github.bayonle010.escrow.escrow.creation.repository.EscrowRepository;
import io.github.bayonle010.escrow.escrow.creation.repository.OutboxEventRepository;
import io.github.bayonle010.escrow.escrow.funding.builder.EscrowFundedEventBuilder;
import io.github.bayonle010.escrow.escrow.funding.domain.EscrowFundingSecuredEvent;
import io.github.bayonle010.escrow.escrow.funding.domain.FundedEscrow;
import io.github.bayonle010.escrow.escrow.funding.domain.InboxRecord;
import io.github.bayonle010.escrow.escrow.funding.repository.EscrowFundingInboxRepository;
import io.github.bayonle010.escrow.escrow.shared.api.ErrorCode;
import io.github.bayonle010.escrow.escrow.shared.exception.EscrowNotFoundException;
import io.github.bayonle010.escrow.escrow.shared.exception.InvalidEscrowException;

@Service
public class EscrowFundingService {

    private static final String CONSUMER_NAME = "escrow-funding-secured-v1";
    private static final String EVENT_TYPE = "EscrowFundingSecured";
    private static final int SUPPORTED_EVENT_VERSION = 1;

    private final EscrowRepository escrowRepository;
    private final EscrowFundingInboxRepository inboxRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final EscrowFundedEventBuilder eventBuilder;
    private final Clock clock;

    public EscrowFundingService(
            EscrowRepository escrowRepository,
            EscrowFundingInboxRepository inboxRepository,
            OutboxEventRepository outboxEventRepository,
            EscrowFundedEventBuilder eventBuilder,
            Clock clock) {
        this.escrowRepository = escrowRepository;
        this.inboxRepository = inboxRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.eventBuilder = eventBuilder;
        this.clock = clock;
    }

    @Transactional
    public FundedEscrow secure(EscrowFundingSecuredEvent event) {
        validateVersion(event);
        Instant fundedAt = clock.instant();
        boolean claimed = inboxRepository.claimEvent(
                CONSUMER_NAME,
                event.eventId(),
                event.escrowId(),
                EVENT_TYPE,
                fundedAt);

        if (!claimed) {
            validateInboxReplay(event);
            return replayExisting(event);
        }

        EscrowEntity escrow = escrowRepository.findByIdForUpdate(event.escrowId())
                .orElseThrow(() -> new EscrowNotFoundException(event.escrowId()));
        validateFinancialTerms(escrow, event);
        validateState(escrow);

        escrow.secureFunding(fundedAt);
        escrowRepository.saveAndFlush(escrow);
        outboxEventRepository.save(eventBuilder.build(escrow, event, fundedAt));
        outboxEventRepository.flush();

        return new FundedEscrow(escrow.getEscrowId(), escrow.getState(), fundedAt, false);
    }

    private void validateVersion(EscrowFundingSecuredEvent event) {
        if (event.eventVersion() != SUPPORTED_EVENT_VERSION) {
            throw invalid(
                    ErrorCode.EVENT_VERSION_UNSUPPORTED,
                    "eventVersion",
                    "EscrowFundingSecured event version " + event.eventVersion() + " is not supported.");
        }
    }

    private void validateInboxReplay(EscrowFundingSecuredEvent event) {
        InboxRecord inbox = inboxRepository.findInbox(CONSUMER_NAME, event.eventId())
                .orElseThrow(() -> new IllegalStateException(
                        "The duplicate funding event inbox record could not be loaded."));
        if (!event.escrowId().equals(inbox.aggregateId()) || !EVENT_TYPE.equals(inbox.eventType())) {
            throw invalid(
                    ErrorCode.EVENT_ID_CONFLICT,
                    "eventId",
                    "The event ID was already used for a different event or escrow.");
        }
    }

    private FundedEscrow replayExisting(EscrowFundingSecuredEvent event) {
        EscrowEntity escrow = escrowRepository.findById(event.escrowId())
                .orElseThrow(() -> new EscrowNotFoundException(event.escrowId()));
        validateFinancialTerms(escrow, event);
        if (escrow.getState() == EscrowState.AWAITING_COUNTERPARTY
                || escrow.getState() == EscrowState.AWAITING_FUNDING
                || escrow.getState() == EscrowState.FUNDING_PROCESSING) {
            throw new IllegalStateException(
                    "The funding event was recorded but escrow " + event.escrowId()
                            + " is still in state " + escrow.getState() + ".");
        }
        return new FundedEscrow(
                escrow.getEscrowId(),
                escrow.getState(),
                escrow.getUpdatedAt(),
                true);
    }

    private void validateFinancialTerms(EscrowEntity escrow, EscrowFundingSecuredEvent event) {
        if (escrow.getAmountMinor() != event.amountMinor()
                || !escrow.getCurrency().equals(event.currency())) {
            throw invalid(
                    ErrorCode.FUNDING_EVENT_CONFLICT,
                    "escrowId",
                    "The secured funding amount or currency does not match escrow "
                            + escrow.getEscrowId() + ".");
        }
    }

    private void validateState(EscrowEntity escrow) {
        if (escrow.getState() != EscrowState.AWAITING_FUNDING) {
            throw invalid(
                    ErrorCode.ESCROW_STATE_INVALID,
                    "state",
                    "Funding cannot be secured while the escrow is in state "
                            + escrow.getState() + ".");
        }
    }

    private InvalidEscrowException invalid(ErrorCode code, String field, String message) {
        return new InvalidEscrowException(code, field, message);
    }
}
