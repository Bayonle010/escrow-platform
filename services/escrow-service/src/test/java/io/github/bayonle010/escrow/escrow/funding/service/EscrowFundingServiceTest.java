package io.github.bayonle010.escrow.escrow.funding.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import io.github.bayonle010.escrow.escrow.creation.domain.EscrowState;
import io.github.bayonle010.escrow.escrow.creation.entity.EscrowEntity;
import io.github.bayonle010.escrow.escrow.creation.entity.OutboxEventEntity;
import io.github.bayonle010.escrow.escrow.creation.repository.EscrowRepository;
import io.github.bayonle010.escrow.escrow.creation.repository.OutboxEventRepository;
import io.github.bayonle010.escrow.escrow.funding.builder.EscrowFundedEventBuilder;
import io.github.bayonle010.escrow.escrow.funding.domain.EscrowFundingSecuredEvent;
import io.github.bayonle010.escrow.escrow.funding.domain.InboxRecord;
import io.github.bayonle010.escrow.escrow.funding.repository.EscrowFundingInboxRepository;
import io.github.bayonle010.escrow.escrow.shared.api.ErrorCode;
import io.github.bayonle010.escrow.escrow.shared.exception.InvalidEscrowException;

class EscrowFundingServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-04T10:19:24.448020Z");
    private static final UUID EVENT_ID = UUID.fromString("01a06bee-2621-7603-812c-4bad587d6956");
    private static final UUID JOURNAL_ID = UUID.fromString("01a06bee-25bd-7d14-96bb-87413b42230f");
    private static final UUID PAYMENT_ID = UUID.fromString("01a069da-eddf-7820-be8d-6c60a54c3259");
    private static final UUID ESCROW_ID = UUID.fromString("01a069d1-82fc-70f5-a5d4-21f81e9f7c4c");
    private static final UUID CORRELATION_ID = UUID.fromString("01a069dd-45ee-752c-806f-51b8ef43497c");
    private static final UUID CAUSATION_ID = UUID.fromString("01a069dd-467e-74c2-9435-c233b0f0f3a0");

    private final EscrowRepository escrowRepository = mock(EscrowRepository.class);
    private final EscrowFundingInboxRepository inboxRepository = mock(EscrowFundingInboxRepository.class);
    private final OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);
    private final EscrowFundedEventBuilder eventBuilder = mock(EscrowFundedEventBuilder.class);
    private final EscrowFundingService service = new EscrowFundingService(
            escrowRepository,
            inboxRepository,
            outboxEventRepository,
            eventBuilder,
            Clock.fixed(NOW, ZoneOffset.UTC));

    private EscrowEntity escrow;
    private EscrowFundingSecuredEvent event;

    @BeforeEach
    void setUp() {
        escrow = mock(EscrowEntity.class);
        event = event(100000, "NGN");
        when(inboxRepository.claimEvent(
                "escrow-funding-secured-v1", EVENT_ID, ESCROW_ID,
                "EscrowFundingSecured", NOW)).thenReturn(true);
        when(escrowRepository.findByIdForUpdate(ESCROW_ID)).thenReturn(Optional.of(escrow));
        when(escrow.getEscrowId()).thenReturn(ESCROW_ID);
        when(escrow.getAmountMinor()).thenReturn(100000L);
        when(escrow.getCurrency()).thenReturn("NGN");
        when(escrow.getState()).thenReturn(EscrowState.AWAITING_FUNDING, EscrowState.FUNDED);
        when(escrow.getVersion()).thenReturn(2L);
        when(eventBuilder.build(escrow, event, NOW)).thenReturn(mock(OutboxEventEntity.class));
    }

    @Test
    void claimsTheEventAndCommitsTheStateAndOutboxTogether() {
        var result = service.secure(event);

        verify(escrow).secureFunding(NOW);
        InOrder order = inOrder(escrowRepository, outboxEventRepository);
        order.verify(escrowRepository).saveAndFlush(escrow);
        order.verify(outboxEventRepository).save(any(OutboxEventEntity.class));
        order.verify(outboxEventRepository).flush();
        assertThat(result.state()).isEqualTo(EscrowState.FUNDED);
        assertThat(result.replayed()).isFalse();
    }

    @Test
    void duplicateDeliveryDoesNotTransitionOrPublishAgain() {
        when(inboxRepository.claimEvent(
                "escrow-funding-secured-v1", EVENT_ID, ESCROW_ID,
                "EscrowFundingSecured", NOW)).thenReturn(false);
        when(inboxRepository.findInbox("escrow-funding-secured-v1", EVENT_ID))
                .thenReturn(Optional.of(new InboxRecord(ESCROW_ID, "EscrowFundingSecured")));
        when(escrowRepository.findById(ESCROW_ID)).thenReturn(Optional.of(escrow));
        when(escrow.getState()).thenReturn(EscrowState.FUNDED);
        when(escrow.getUpdatedAt()).thenReturn(NOW);

        var result = service.secure(event);

        assertThat(result.replayed()).isTrue();
        verify(escrow, never()).secureFunding(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void rejectsFundingThatDoesNotMatchTheEscrowAmount() {
        event = event(99999, "NGN");

        assertThatThrownBy(() -> service.secure(event))
                .isInstanceOfSatisfying(InvalidEscrowException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.FUNDING_EVENT_CONFLICT));
        verify(escrow, never()).secureFunding(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void rejectsAFirstDeliveryWhenEscrowIsNotAwaitingFunding() {
        when(escrow.getState()).thenReturn(EscrowState.FUNDED);

        assertThatThrownBy(() -> service.secure(event))
                .isInstanceOfSatisfying(InvalidEscrowException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.ESCROW_STATE_INVALID));
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void rejectsAnEventIdPreviouslyUsedForAnotherEscrow() {
        UUID anotherEscrowId = UUID.fromString("019c0000-0000-7000-8000-000000000099");
        when(inboxRepository.claimEvent(
                "escrow-funding-secured-v1", EVENT_ID, ESCROW_ID,
                "EscrowFundingSecured", NOW)).thenReturn(false);
        when(inboxRepository.findInbox("escrow-funding-secured-v1", EVENT_ID))
                .thenReturn(Optional.of(new InboxRecord(anotherEscrowId, "EscrowFundingSecured")));

        assertThatThrownBy(() -> service.secure(event))
                .isInstanceOfSatisfying(InvalidEscrowException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.EVENT_ID_CONFLICT));
        verify(escrowRepository, never()).findById(any());
    }

    private EscrowFundingSecuredEvent event(long amountMinor, String currency) {
        return new EscrowFundingSecuredEvent(
                EVENT_ID,
                1,
                NOW,
                JOURNAL_ID,
                PAYMENT_ID,
                ESCROW_ID,
                amountMinor,
                currency,
                CORRELATION_ID,
                CAUSATION_ID);
    }
}
