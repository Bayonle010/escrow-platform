package io.github.bayonle010.escrow.ledger.funding.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.bayonle010.escrow.ledger.funding.builder.EscrowFundingSecuredEventBuilder;
import io.github.bayonle010.escrow.ledger.funding.domain.AccountSide;
import io.github.bayonle010.escrow.ledger.funding.domain.FundingSecured;
import io.github.bayonle010.escrow.ledger.funding.domain.InboxRecord;
import io.github.bayonle010.escrow.ledger.funding.domain.LedgerAccount;
import io.github.bayonle010.escrow.ledger.funding.domain.LedgerEntry;
import io.github.bayonle010.escrow.ledger.funding.domain.PaymentSucceededEvent;
import io.github.bayonle010.escrow.ledger.funding.domain.PostedFunding;
import io.github.bayonle010.escrow.ledger.funding.repository.LedgerFundingRepository;
import io.github.bayonle010.escrow.ledger.shared.UuidV7Generator;
import io.github.bayonle010.escrow.ledger.shared.api.ErrorCode;
import io.github.bayonle010.escrow.ledger.shared.exception.LedgerApiException;

@Service
public class LedgerFundingService {

    private static final String CONSUMER_NAME = "ledger-payment-succeeded-v1";
    private static final String EVENT_TYPE = "PaymentSucceeded";
    private static final int SUPPORTED_EVENT_VERSION = 1;

    private final LedgerFundingRepository repository;
    private final JournalBalanceValidator balanceValidator;
    private final EscrowFundingSecuredEventBuilder eventBuilder;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public LedgerFundingService(
            LedgerFundingRepository repository,
            JournalBalanceValidator balanceValidator,
            EscrowFundingSecuredEventBuilder eventBuilder,
            UuidV7Generator uuidGenerator,
            Clock clock) {
        this.repository = repository;
        this.balanceValidator = balanceValidator;
        this.eventBuilder = eventBuilder;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public FundingSecured secure(PaymentSucceededEvent event) {
        validateEvent(event);
        Instant securedAt = clock.instant();

        boolean claimed = repository.claimEvent(
                CONSUMER_NAME,
                event.eventId(),
                event.paymentId(),
                EVENT_TYPE,
                securedAt);
        if (!claimed) {
            validateInboxReplay(event);
            return replayExisting(event);
        }

        UUID journalId = uuidGenerator.generate();
        boolean inserted = repository.insertFundingJournal(
                journalId,
                businessReference(event.paymentId()),
                event.paymentId(),
                event.escrowId(),
                event.amountMinor(),
                event.currency(),
                event.provider(),
                event.providerReference(),
                event.correlationId(),
                event.eventId(),
                securedAt);
        if (!inserted) {
            return replayExisting(event);
        }

        LedgerAccount providerClearing = repository.getOrCreateAccount(
                uuidGenerator.generate(),
                "PROVIDER",
                event.provider(),
                "PROVIDER_CLEARING",
                AccountSide.DEBIT,
                event.currency(),
                securedAt);
        LedgerAccount escrowHeld = repository.getOrCreateAccount(
                uuidGenerator.generate(),
                "ESCROW",
                event.escrowId().toString(),
                "ESCROW_HELD",
                AccountSide.CREDIT,
                event.currency(),
                securedAt);
        validateAccount(providerClearing, AccountSide.DEBIT);
        validateAccount(escrowHeld, AccountSide.CREDIT);

        repository.ensureBalance(providerClearing.accountId(), securedAt);
        repository.ensureBalance(escrowHeld.accountId(), securedAt);
        repository.lockBalances(providerClearing.accountId(), escrowHeld.accountId());

        List<LedgerEntry> entries = List.of(
                new LedgerEntry(
                        uuidGenerator.generate(),
                        journalId,
                        providerClearing.accountId(),
                        AccountSide.DEBIT,
                        event.amountMinor(),
                        event.currency(),
                        1,
                        securedAt),
                new LedgerEntry(
                        uuidGenerator.generate(),
                        journalId,
                        escrowHeld.accountId(),
                        AccountSide.CREDIT,
                        event.amountMinor(),
                        event.currency(),
                        2,
                        securedAt));
        balanceValidator.validate(entries);
        entries.forEach(repository::insertEntry);

        repository.increaseNormalBalance(providerClearing.accountId(), event.amountMinor(), securedAt);
        repository.increaseNormalBalance(escrowHeld.accountId(), event.amountMinor(), securedAt);

        PostedFunding funding = new PostedFunding(
                journalId,
                event.paymentId(),
                event.escrowId(),
                event.amountMinor(),
                event.currency(),
                event.provider(),
                event.providerReference(),
                providerClearing.accountId(),
                escrowHeld.accountId(),
                securedAt);
        repository.insertOutboxEvent(eventBuilder.build(funding, event, securedAt));
        return toDomain(funding, false);
    }

    private void validateEvent(PaymentSucceededEvent event) {
        if (event.eventVersion() != SUPPORTED_EVENT_VERSION) {
            throw new LedgerApiException(
                    ErrorCode.EVENT_VERSION_UNSUPPORTED,
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "eventVersion",
                    "PaymentSucceeded event version " + event.eventVersion() + " is not supported.");
        }
    }

    private void validateInboxReplay(PaymentSucceededEvent event) {
        InboxRecord inbox = repository.findInbox(CONSUMER_NAME, event.eventId())
                .orElseThrow(() -> postingFailure("The duplicate event inbox record could not be loaded."));
        if (!event.paymentId().equals(inbox.aggregateId()) || !EVENT_TYPE.equals(inbox.eventType())) {
            throw new LedgerApiException(
                    ErrorCode.EVENT_ID_CONFLICT,
                    HttpStatus.CONFLICT,
                    "eventId",
                    "The event ID was already used for a different event or payment.");
        }
    }

    private FundingSecured replayExisting(PaymentSucceededEvent event) {
        PostedFunding existing = repository.findFunding(event.paymentId())
                .orElseThrow(() -> postingFailure("The existing funding journal could not be loaded."));
        if (!event.escrowId().equals(existing.escrowId())
                || event.amountMinor() != existing.amountMinor()
                || !event.currency().equals(existing.currency())
                || !event.provider().equals(existing.provider())
                || !event.providerReference().equals(existing.providerReference())) {
            throw new LedgerApiException(
                    ErrorCode.FUNDING_EVENT_CONFLICT,
                    HttpStatus.CONFLICT,
                    "paymentId",
                    "Payment " + event.paymentId()
                            + " already has a funding journal with different financial terms.");
        }
        return toDomain(existing, true);
    }

    private void validateAccount(LedgerAccount account, AccountSide expectedNormalSide) {
        if (account.normalSide() != expectedNormalSide) {
            throw postingFailure("Ledger account " + account.accountId() + " has an invalid normal side.");
        }
        if (!"ACTIVE".equals(account.status())) {
            throw postingFailure("Ledger account " + account.accountId() + " is not active.");
        }
    }

    private FundingSecured toDomain(PostedFunding funding, boolean replayed) {
        return new FundingSecured(
                funding.journalId(),
                funding.paymentId(),
                funding.escrowId(),
                funding.amountMinor(),
                funding.currency(),
                funding.providerClearingAccountId(),
                funding.escrowHeldAccountId(),
                funding.securedAt(),
                replayed);
    }

    private String businessReference(UUID paymentId) {
        return "FUNDING:" + paymentId;
    }

    private LedgerApiException postingFailure(String message) {
        return new LedgerApiException(
                ErrorCode.LEDGER_POSTING_FAILED,
                HttpStatus.INTERNAL_SERVER_ERROR,
                null,
                message);
    }
}
