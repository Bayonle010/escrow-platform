package io.github.bayonle010.escrow.escrow.creation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.github.bayonle010.escrow.escrow.creation.domain.EscrowCreation;
import io.github.bayonle010.escrow.escrow.creation.domain.EscrowState;
import io.github.bayonle010.escrow.escrow.creation.dto.CreateEscrowRequest;
import io.github.bayonle010.escrow.escrow.shared.api.ErrorCode;
import io.github.bayonle010.escrow.escrow.shared.exception.InvalidEscrowException;

class EscrowCreationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-20T12:00:00Z");
    private static final UUID BUYER_ID = UUID.fromString("019c0000-0000-7000-8000-000000000001");
    private static final UUID SELLER_ID = UUID.fromString("019c0000-0000-7000-8000-000000000002");
    private static final UUID CORRELATION_ID = UUID.fromString("019c0000-0000-7000-8000-000000000010");

    private final EscrowCreationPersistenceService persistenceService =
            mock(EscrowCreationPersistenceService.class);
    private final EscrowCreationService service = new EscrowCreationService(
            persistenceService,
            Clock.fixed(NOW, ZoneOffset.UTC),
            Set.of("EUR", "GBP", "NGN", "USD"));

    @Test
    void normalizesTermsAndCreatesTheInitialEscrowState() {
        UUID escrowId = UUID.fromString("019c0000-0000-7000-8000-000000000020");
        when(persistenceService.save(any(EscrowCreation.class))).thenReturn(escrowId);

        var result = service.create(validRequest(" ngn "), CORRELATION_ID);

        ArgumentCaptor<EscrowCreation> creation = ArgumentCaptor.forClass(EscrowCreation.class);
        verify(persistenceService).save(creation.capture());
        assertThat(creation.getValue().currency()).isEqualTo("NGN");
        assertThat(creation.getValue().category()).isEqualTo("GOODS");
        assertThat(creation.getValue().description()).isEqualTo("Professional camera");
        assertThat(creation.getValue().termsVersion()).isEqualTo(1);
        assertThat(creation.getValue().state()).isEqualTo(EscrowState.AWAITING_COUNTERPARTY);
        assertThat(creation.getValue().createdAt()).isEqualTo(NOW);
        assertThat(creation.getValue().correlationId()).isEqualTo(CORRELATION_ID);
        assertThat(result.escrowId()).isEqualTo(escrowId);
        assertThat(result.state()).isEqualTo(EscrowState.AWAITING_COUNTERPARTY);
    }

    @Test
    void rejectsTheSameBuyerAndSeller() {
        CreateEscrowRequest request = new CreateEscrowRequest(
                BUYER_ID, BUYER_ID, BUYER_ID, 100000, "NGN", "Camera", "goods",
                NOW.plusSeconds(3600), 7, "Release", "Refund");

        assertThatThrownBy(() -> service.create(request, CORRELATION_ID))
                .isInstanceOfSatisfying(InvalidEscrowException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.ESCROW_PARTICIPANTS_MUST_DIFFER));

        verifyNoInteractions(persistenceService);
    }

    @Test
    void rejectsACreatorWhoIsNotAParticipant() {
        UUID outsiderId = UUID.fromString("019c0000-0000-7000-8000-000000000003");
        CreateEscrowRequest request = new CreateEscrowRequest(
                BUYER_ID, SELLER_ID, outsiderId, 100000, "NGN", "Camera", "goods",
                NOW.plusSeconds(3600), 7, "Release", "Refund");

        assertThatThrownBy(() -> service.create(request, CORRELATION_ID))
                .isInstanceOfSatisfying(InvalidEscrowException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.ESCROW_CREATOR_NOT_PARTICIPANT));

        verifyNoInteractions(persistenceService);
    }

    @Test
    void rejectsUnsupportedCurrency() {
        assertThatThrownBy(() -> service.create(validRequest("JPY"), CORRELATION_ID))
                .isInstanceOfSatisfying(InvalidEscrowException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.ESCROW_UNSUPPORTED_CURRENCY));

        verifyNoInteractions(persistenceService);
    }

    @Test
    void enforcesAmountAndDeadlineRulesInsideTheService() {
        CreateEscrowRequest invalidAmount = new CreateEscrowRequest(
                BUYER_ID, SELLER_ID, BUYER_ID, 0, "NGN", "Camera", "goods",
                NOW.plusSeconds(3600), 7, "Release", "Refund");
        CreateEscrowRequest invalidDeadline = new CreateEscrowRequest(
                BUYER_ID, SELLER_ID, BUYER_ID, 100000, "NGN", "Camera", "goods",
                NOW, 7, "Release", "Refund");

        assertThatThrownBy(() -> service.create(invalidAmount, CORRELATION_ID))
                .isInstanceOfSatisfying(InvalidEscrowException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.ESCROW_AMOUNT_INVALID));
        assertThatThrownBy(() -> service.create(invalidDeadline, CORRELATION_ID))
                .isInstanceOfSatisfying(InvalidEscrowException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.ESCROW_DELIVERY_DEADLINE_INVALID));

        verifyNoInteractions(persistenceService);
    }

    private CreateEscrowRequest validRequest(String currency) {
        return new CreateEscrowRequest(
                BUYER_ID,
                SELLER_ID,
                BUYER_ID,
                100000,
                currency,
                " Professional camera ",
                " goods ",
                NOW.plusSeconds(86400),
                7,
                " Release after accepted delivery ",
                " Refund if delivery misses the deadline ");
    }
}
