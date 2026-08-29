package io.github.bayonle010.escrow.ledger.funding.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.github.bayonle010.escrow.ledger.funding.builder.EscrowFundingSecuredEventBuilder;
import io.github.bayonle010.escrow.ledger.funding.domain.AccountSide;
import io.github.bayonle010.escrow.ledger.funding.domain.InboxRecord;
import io.github.bayonle010.escrow.ledger.funding.domain.LedgerAccount;
import io.github.bayonle010.escrow.ledger.funding.domain.LedgerEntry;
import io.github.bayonle010.escrow.ledger.funding.domain.OutboxEvent;
import io.github.bayonle010.escrow.ledger.funding.domain.PaymentSucceededEvent;
import io.github.bayonle010.escrow.ledger.funding.domain.PostedFunding;
import io.github.bayonle010.escrow.ledger.funding.repository.LedgerFundingRepository;
import io.github.bayonle010.escrow.ledger.shared.UuidV7Generator;
import io.github.bayonle010.escrow.ledger.shared.api.ErrorCode;
import io.github.bayonle010.escrow.ledger.shared.exception.LedgerApiException;

class LedgerFundingServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-28T00:00:00Z");
    private static final UUID EVENT_ID = UUID.fromString("019c0000-0000-7000-8000-000000000040");
    private static final UUID PAYMENT_ID = UUID.fromString("019c0000-0000-7000-8000-000000000030");
    private static final UUID ESCROW_ID = UUID.fromString("019c0000-0000-7000-8000-000000000020");
    private static final UUID PAYER_ID = UUID.fromString("019c0000-0000-7000-8000-000000000001");
    private static final UUID JOURNAL_ID = UUID.fromString("019c0000-0000-7000-8000-000000000050");
    private static final UUID PROVIDER_ACCOUNT_ID = UUID.fromString("019c0000-0000-7000-8000-000000000051");
    private static final UUID ESCROW_ACCOUNT_ID = UUID.fromString("019c0000-0000-7000-8000-000000000052");
    private static final UUID CORRELATION_ID = UUID.fromString("019c0000-0000-7000-8000-000000000010");

    private final LedgerFundingRepository repository = mock(LedgerFundingRepository.class);
    private final JournalBalanceValidator validator = new JournalBalanceValidator();
    private final EscrowFundingSecuredEventBuilder eventBuilder = mock(EscrowFundingSecuredEventBuilder.class);
    private final UuidV7Generator uuidGenerator = mock(UuidV7Generator.class);
    private final LedgerFundingService service = new LedgerFundingService(
            repository,
            validator,
            eventBuilder,
            uuidGenerator,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void postsOneBalancedFundingJournalAndOutboxEvent() {
        PaymentSucceededEvent event = event(EVENT_ID, 100000);
        LedgerAccount providerAccount = account(PROVIDER_ACCOUNT_ID, "PROVIDER", "SIMULATED",
                "PROVIDER_CLEARING", AccountSide.DEBIT);
        LedgerAccount escrowAccount = account(ESCROW_ACCOUNT_ID, "ESCROW", ESCROW_ID.toString(),
                "ESCROW_HELD", AccountSide.CREDIT);
        OutboxEvent outboxEvent = mock(OutboxEvent.class);
        when(repository.claimEvent(anyString(), eq(EVENT_ID), eq(PAYMENT_ID), anyString(), eq(NOW)))
                .thenReturn(true);
        when(uuidGenerator.generate())
                .thenReturn(JOURNAL_ID, PROVIDER_ACCOUNT_ID, ESCROW_ACCOUNT_ID,
                        UUID.randomUUID(), UUID.randomUUID());
        when(repository.insertFundingJournal(
                eq(JOURNAL_ID), anyString(), eq(PAYMENT_ID), eq(ESCROW_ID), eq(100000L),
                eq("NGN"), eq("SIMULATED"), eq("provider-reference"),
                eq(CORRELATION_ID), eq(EVENT_ID), eq(NOW)))
                .thenReturn(true);
        when(repository.getOrCreateAccount(
                eq(PROVIDER_ACCOUNT_ID), eq("PROVIDER"), eq("SIMULATED"),
                eq("PROVIDER_CLEARING"), eq(AccountSide.DEBIT), eq("NGN"), eq(NOW)))
                .thenReturn(providerAccount);
        when(repository.getOrCreateAccount(
                eq(ESCROW_ACCOUNT_ID), eq("ESCROW"), eq(ESCROW_ID.toString()),
                eq("ESCROW_HELD"), eq(AccountSide.CREDIT), eq("NGN"), eq(NOW)))
                .thenReturn(escrowAccount);
        when(eventBuilder.build(any(PostedFunding.class), eq(event), eq(NOW))).thenReturn(outboxEvent);

        var result = service.secure(event);

        assertThat(result.replayed()).isFalse();
        assertThat(result.journalId()).isEqualTo(JOURNAL_ID);
        ArgumentCaptor<LedgerEntry> entries = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(repository, times(2)).insertEntry(entries.capture());
        assertThat(entries.getAllValues()).extracting(LedgerEntry::direction)
                .containsExactly(AccountSide.DEBIT, AccountSide.CREDIT);
        assertThat(entries.getAllValues()).extracting(LedgerEntry::amountMinor)
                .containsExactly(100000L, 100000L);
        verify(repository).insertOutboxEvent(outboxEvent);
    }

    @Test
    void safelyReplaysAnAlreadyProcessedEvent() {
        PaymentSucceededEvent event = event(EVENT_ID, 100000);
        when(repository.claimEvent(anyString(), eq(EVENT_ID), eq(PAYMENT_ID), anyString(), eq(NOW)))
                .thenReturn(false);
        when(repository.findInbox(anyString(), eq(EVENT_ID)))
                .thenReturn(Optional.of(new InboxRecord(PAYMENT_ID, "PaymentSucceeded")));
        when(repository.findFunding(PAYMENT_ID)).thenReturn(Optional.of(postedFunding(100000)));

        var result = service.secure(event);

        assertThat(result.replayed()).isTrue();
        assertThat(result.journalId()).isEqualTo(JOURNAL_ID);
        verify(repository, never()).insertFundingJournal(
                any(), anyString(), any(), any(), anyLong(), anyString(), anyString(), anyString(),
                any(), any(), any());
        verify(repository, never()).insertOutboxEvent(any());
    }

    @Test
    void rejectsConflictingFinancialTermsForTheSamePayment() {
        PaymentSucceededEvent event = event(UUID.randomUUID(), 100001);
        when(repository.claimEvent(anyString(), eq(event.eventId()), eq(PAYMENT_ID), anyString(), eq(NOW)))
                .thenReturn(true);
        when(uuidGenerator.generate()).thenReturn(UUID.randomUUID());
        when(repository.insertFundingJournal(
                any(), anyString(), eq(PAYMENT_ID), eq(ESCROW_ID), eq(100001L), eq("NGN"),
                eq("SIMULATED"), eq("provider-reference"), eq(CORRELATION_ID),
                eq(event.eventId()), eq(NOW)))
                .thenReturn(false);
        when(repository.findFunding(PAYMENT_ID)).thenReturn(Optional.of(postedFunding(100000)));

        assertThatThrownBy(() -> service.secure(event))
                .isInstanceOfSatisfying(
                        LedgerApiException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.FUNDING_EVENT_CONFLICT));
    }

    private PaymentSucceededEvent event(UUID eventId, long amountMinor) {
        return new PaymentSucceededEvent(
                eventId,
                1,
                NOW.minusSeconds(10),
                PAYMENT_ID,
                ESCROW_ID,
                PAYER_ID,
                amountMinor,
                "NGN",
                "SIMULATED",
                "provider-reference",
                1,
                CORRELATION_ID);
    }

    private LedgerAccount account(
            UUID accountId,
            String ownerType,
            String ownerReference,
            String accountType,
            AccountSide normalSide) {
        return new LedgerAccount(
                accountId, ownerType, ownerReference, accountType, normalSide, "NGN", "ACTIVE");
    }

    private PostedFunding postedFunding(long amountMinor) {
        return new PostedFunding(
                JOURNAL_ID,
                PAYMENT_ID,
                ESCROW_ID,
                amountMinor,
                "NGN",
                "SIMULATED",
                "provider-reference",
                PROVIDER_ACCOUNT_ID,
                ESCROW_ACCOUNT_ID,
                NOW);
    }
}
