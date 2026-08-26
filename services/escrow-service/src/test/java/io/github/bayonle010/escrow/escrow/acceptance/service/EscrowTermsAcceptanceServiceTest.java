package io.github.bayonle010.escrow.escrow.acceptance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import io.github.bayonle010.escrow.escrow.acceptance.builder.EscrowTermsAcceptanceEventBuilder;
import io.github.bayonle010.escrow.escrow.acceptance.dto.AcceptEscrowTermsRequest;
import io.github.bayonle010.escrow.escrow.acceptance.entity.EscrowTermsAcceptanceEntity;
import io.github.bayonle010.escrow.escrow.acceptance.repository.EscrowTermsAcceptanceRepository;
import io.github.bayonle010.escrow.escrow.creation.domain.EscrowState;
import io.github.bayonle010.escrow.escrow.creation.entity.EscrowEntity;
import io.github.bayonle010.escrow.escrow.creation.entity.EscrowTermsEntity;
import io.github.bayonle010.escrow.escrow.creation.entity.OutboxEventEntity;
import io.github.bayonle010.escrow.escrow.creation.repository.EscrowRepository;
import io.github.bayonle010.escrow.escrow.creation.repository.EscrowTermsRepository;
import io.github.bayonle010.escrow.escrow.creation.repository.OutboxEventRepository;
import io.github.bayonle010.escrow.escrow.shared.api.ErrorCode;
import io.github.bayonle010.escrow.escrow.shared.exception.EscrowNotFoundException;
import io.github.bayonle010.escrow.escrow.shared.exception.InvalidEscrowException;

class EscrowTermsAcceptanceServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-20T12:00:00Z");
    private static final UUID ESCROW_ID = UUID.fromString("019c0000-0000-7000-8000-000000000020");
    private static final UUID BUYER_ID = UUID.fromString("019c0000-0000-7000-8000-000000000001");
    private static final UUID SELLER_ID = UUID.fromString("019c0000-0000-7000-8000-000000000002");
    private static final UUID CORRELATION_ID = UUID.fromString("019c0000-0000-7000-8000-000000000010");
    private static final UUID ACCEPTANCE_REFERENCE =
            UUID.fromString("019c0000-0000-7000-8000-000000000030");

    private final EscrowRepository escrowRepository = mock(EscrowRepository.class);
    private final EscrowTermsRepository termsRepository = mock(EscrowTermsRepository.class);
    private final EscrowTermsAcceptanceRepository acceptanceRepository =
            mock(EscrowTermsAcceptanceRepository.class);
    private final OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);
    private final EscrowTermsAcceptanceEventBuilder eventBuilder =
            mock(EscrowTermsAcceptanceEventBuilder.class);
    private final EscrowTermsAcceptanceService service = new EscrowTermsAcceptanceService(
            escrowRepository,
            termsRepository,
            acceptanceRepository,
            outboxEventRepository,
            eventBuilder,
            Clock.fixed(NOW, ZoneOffset.UTC));

    private EscrowEntity escrow;
    private EscrowTermsEntity terms;

    @BeforeEach
    void setUp() {
        escrow = mock(EscrowEntity.class);
        terms = mock(EscrowTermsEntity.class);
        when(escrowRepository.findById(ESCROW_ID)).thenReturn(Optional.of(escrow));
        when(escrow.getState()).thenReturn(
                EscrowState.AWAITING_COUNTERPARTY,
                EscrowState.AWAITING_FUNDING);
        when(escrow.getCurrentTermsVersion()).thenReturn(1);
        when(escrow.getBuyerId()).thenReturn(BUYER_ID);
        when(escrow.getSellerId()).thenReturn(SELLER_ID);
        when(escrow.getDeliveryDeadline()).thenReturn(NOW.plusSeconds(86400));
        when(escrow.getVersion()).thenReturn(1L);
        when(termsRepository.findByEscrowIdAndTermsVersion(ESCROW_ID, 1))
                .thenReturn(Optional.of(terms));
        when(terms.getCreatedBy()).thenReturn(BUYER_ID);
        when(acceptanceRepository.save(any(EscrowTermsAcceptanceEntity.class)))
                .thenReturn(EscrowTermsAcceptanceEntity.builder()
                        .acceptanceReference(ACCEPTANCE_REFERENCE)
                        .escrowId(ESCROW_ID)
                        .termsVersion(1)
                        .participantId(SELLER_ID)
                        .acceptedAt(NOW)
                        .build());
        when(eventBuilder.build(ESCROW_ID, SELLER_ID, 1, 2, NOW, CORRELATION_ID))
                .thenReturn(mock(OutboxEventEntity.class));
    }

    @Test
    void acceptsCurrentTermsAndPersistsTheTransitionBeforeTheEvent() {
        var result = service.accept(
                ESCROW_ID,
                new AcceptEscrowTermsRequest(SELLER_ID, 1),
                CORRELATION_ID);

        verify(escrow).acceptTerms(NOW);
        InOrder order = inOrder(escrowRepository, acceptanceRepository, outboxEventRepository);
        order.verify(escrowRepository).saveAndFlush(escrow);
        order.verify(acceptanceRepository).save(any(EscrowTermsAcceptanceEntity.class));
        order.verify(outboxEventRepository).save(any(OutboxEventEntity.class));
        order.verify(outboxEventRepository).flush();
        verify(eventBuilder).build(ESCROW_ID, SELLER_ID, 1, 2, NOW, CORRELATION_ID);
        assertThat(result.acceptanceReference()).isEqualTo(ACCEPTANCE_REFERENCE);
        assertThat(result.acceptedBy()).isEqualTo(SELLER_ID);
        assertThat(result.state()).isEqualTo(EscrowState.AWAITING_FUNDING);
    }

    @Test
    void rejectsAnUnknownEscrow() {
        when(escrowRepository.findById(ESCROW_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.accept(
                ESCROW_ID,
                new AcceptEscrowTermsRequest(SELLER_ID, 1),
                CORRELATION_ID))
                .isInstanceOf(EscrowNotFoundException.class);

        verifyNoInteractions(acceptanceRepository, outboxEventRepository);
    }

    @Test
    void rejectsTheCreatorInsteadOfTheCounterparty() {
        assertInvalid(
                new AcceptEscrowTermsRequest(BUYER_ID, 1),
                ErrorCode.ESCROW_ACCEPTOR_NOT_COUNTERPARTY);
    }

    @Test
    void rejectsAStaleTermsVersion() {
        assertInvalid(
                new AcceptEscrowTermsRequest(SELLER_ID, 2),
                ErrorCode.ESCROW_TERMS_VERSION_MISMATCH);
        verify(termsRepository, never()).findByEscrowIdAndTermsVersion(any(), any(Integer.class));
    }

    @Test
    void rejectsAnEscrowThatIsNotAwaitingTheCounterparty() {
        when(escrow.getState()).thenReturn(EscrowState.AWAITING_FUNDING);

        assertInvalid(
                new AcceptEscrowTermsRequest(SELLER_ID, 1),
                ErrorCode.ESCROW_STATE_INVALID);
    }

    @Test
    void rejectsAcceptanceAfterTheDeliveryDeadline() {
        when(escrow.getDeliveryDeadline()).thenReturn(NOW);

        assertInvalid(
                new AcceptEscrowTermsRequest(SELLER_ID, 1),
                ErrorCode.ESCROW_TERMS_EXPIRED);
    }

    private void assertInvalid(AcceptEscrowTermsRequest request, ErrorCode errorCode) {
        assertThatThrownBy(() -> service.accept(ESCROW_ID, request, CORRELATION_ID))
                .isInstanceOfSatisfying(InvalidEscrowException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode));
        verifyNoInteractions(acceptanceRepository, outboxEventRepository);
    }
}
