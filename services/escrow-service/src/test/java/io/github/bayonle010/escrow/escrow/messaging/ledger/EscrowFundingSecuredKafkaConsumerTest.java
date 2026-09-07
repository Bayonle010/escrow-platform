package io.github.bayonle010.escrow.escrow.messaging.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.Validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.github.bayonle010.escrow.escrow.creation.domain.EscrowState;
import io.github.bayonle010.escrow.escrow.funding.domain.EscrowFundingSecuredEvent;
import io.github.bayonle010.escrow.escrow.funding.domain.FundedEscrow;
import io.github.bayonle010.escrow.escrow.funding.service.EscrowFundingService;
import tools.jackson.databind.json.JsonMapper;

class EscrowFundingSecuredKafkaConsumerTest {

    private static final UUID EVENT_ID = UUID.fromString("01a06bee-2621-7603-812c-4bad587d6956");
    private static final UUID JOURNAL_ID = UUID.fromString("01a06bee-25bd-7d14-96bb-87413b42230f");
    private static final UUID PAYMENT_ID = UUID.fromString("01a069da-eddf-7820-be8d-6c60a54c3259");
    private static final UUID ESCROW_ID = UUID.fromString("01a069d1-82fc-70f5-a5d4-21f81e9f7c4c");
    private static final UUID CORRELATION_ID = UUID.fromString("01a069dd-45ee-752c-806f-51b8ef43497c");
    private static final UUID CAUSATION_ID = UUID.fromString("01a069dd-467e-74c2-9435-c233b0f0f3a0");

    private final EscrowFundingService fundingService = mock(EscrowFundingService.class);
    private EscrowFundingSecuredKafkaConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new EscrowFundingSecuredKafkaConsumer(
                JsonMapper.builder().findAndAddModules().build(),
                Validation.buildDefaultValidatorFactory().getValidator(),
                fundingService);
        when(fundingService.secure(any())).thenReturn(new FundedEscrow(
                ESCROW_ID, EscrowState.FUNDED, Instant.parse("2026-09-04T10:20:00Z"), false));
    }

    @Test
    void mapsAValidHistoricalLedgerEventWithinTimestampTolerance() {
        consumer.consume(validEventJson());

        ArgumentCaptor<EscrowFundingSecuredEvent> captor =
                ArgumentCaptor.forClass(EscrowFundingSecuredEvent.class);
        verify(fundingService).secure(captor.capture());
        assertThat(captor.getValue().eventId()).isEqualTo(EVENT_ID);
        assertThat(captor.getValue().journalId()).isEqualTo(JOURNAL_ID);
        assertThat(captor.getValue().paymentId()).isEqualTo(PAYMENT_ID);
        assertThat(captor.getValue().escrowId()).isEqualTo(ESCROW_ID);
        assertThat(captor.getValue().amountMinor()).isEqualTo(100000);
    }

    @Test
    void ignoresOtherValidLedgerEventTypes() {
        consumer.consume(validEventJson().replace("EscrowFundingSecured", "EscrowFundsReleased"));

        verify(fundingService, never()).secure(any());
    }

    @Test
    void rejectsEnvelopeAndPayloadCausationThatDisagree() {
        String inconsistent = validEventJson().replaceFirst(
                CAUSATION_ID.toString(),
                "019c0000-0000-7000-8000-000000000099");

        assertThatThrownBy(() -> consumer.consume(inconsistent))
                .isInstanceOf(InvalidLedgerEventException.class)
                .hasMessageContaining("does not match");
        verify(fundingService, never()).secure(any());
    }

    @Test
    void rejectsInvalidFinancialPayload() {
        String invalid = validEventJson().replace("\"amountMinor\":100000", "\"amountMinor\":0");

        assertThatThrownBy(() -> consumer.consume(invalid))
                .isInstanceOf(InvalidLedgerEventException.class)
                .hasMessageContaining("validation failed");
        verify(fundingService, never()).secure(any());
    }

    private String validEventJson() {
        return """
                {
                  "eventId":"%s",
                  "aggregateType":"LedgerJournal",
                  "aggregateId":"%s",
                  "eventType":"EscrowFundingSecured",
                  "eventVersion":1,
                  "occurredAt":"2026-09-04T10:19:24.448020Z",
                  "correlationId":"%s",
                  "causationId":"%s",
                  "payload":{
                    "eventType":"EscrowFundingSecured",
                    "eventVersion":1,
                    "occurredAt":"2026-09-04T10:19:24.448020457Z",
                    "journalId":"%s",
                    "paymentId":"%s",
                    "escrowId":"%s",
                    "amountMinor":100000,
                    "currency":"NGN",
                    "correlationId":"%s",
                    "causationId":"%s"
                  }
                }
                """.formatted(
                EVENT_ID, JOURNAL_ID, CORRELATION_ID, CAUSATION_ID,
                JOURNAL_ID, PAYMENT_ID, ESCROW_ID, CORRELATION_ID, CAUSATION_ID);
    }
}
