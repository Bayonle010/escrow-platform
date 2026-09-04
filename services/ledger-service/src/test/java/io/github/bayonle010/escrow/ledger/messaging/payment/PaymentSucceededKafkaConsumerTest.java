package io.github.bayonle010.escrow.ledger.messaging.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.bayonle010.escrow.ledger.funding.domain.FundingSecured;
import io.github.bayonle010.escrow.ledger.funding.domain.PaymentSucceededEvent;
import io.github.bayonle010.escrow.ledger.funding.service.LedgerFundingService;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class PaymentSucceededKafkaConsumerTest {

    private static final UUID EVENT_ID = UUID.fromString("019c0000-0000-7000-8000-000000000040");
    private static final UUID PAYMENT_ID = UUID.fromString("019c0000-0000-7000-8000-000000000030");
    private static final UUID ESCROW_ID = UUID.fromString("019c0000-0000-7000-8000-000000000020");
    private static final UUID PAYER_ID = UUID.fromString("019c0000-0000-7000-8000-000000000001");
    private static final UUID CORRELATION_ID = UUID.fromString("019c0000-0000-7000-8000-000000000010");
    private static final UUID JOURNAL_ID = UUID.fromString("019c0000-0000-7000-8000-000000000050");
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-28T00:00:00Z");

    @Mock
    private LedgerFundingService fundingService;

    private PaymentSucceededKafkaConsumer consumer;

    @BeforeEach
    void setUp() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        consumer = new PaymentSucceededKafkaConsumer(
                JsonMapper.builder().findAndAddModules().build(),
                validator,
                fundingService);
    }

    @Test
    void mapsAValidKafkaEnvelopeIntoTheExistingLedgerTransaction() {
        when(fundingService.secure(any())).thenReturn(fundingSecured());

        consumer.consume(validEventJson());

        ArgumentCaptor<PaymentSucceededEvent> eventCaptor =
                ArgumentCaptor.forClass(PaymentSucceededEvent.class);
        verify(fundingService).secure(eventCaptor.capture());
        PaymentSucceededEvent event = eventCaptor.getValue();
        assertThat(event.eventId()).isEqualTo(EVENT_ID);
        assertThat(event.paymentId()).isEqualTo(PAYMENT_ID);
        assertThat(event.escrowId()).isEqualTo(ESCROW_ID);
        assertThat(event.amountMinor()).isEqualTo(100000);
        assertThat(event.correlationId()).isEqualTo(CORRELATION_ID);
    }

    @Test
    void acceptsSubMicrosecondTimestampPrecisionLostByTheOutboxDatabase() {
        when(fundingService.secure(any())).thenReturn(fundingSecured());
        Instant payloadOccurredAt = Instant.parse("2026-08-28T00:00:00.000000268Z");

        consumer.consume(eventJson(OCCURRED_AT, payloadOccurredAt));

        verify(fundingService).secure(any());
    }

    @Test
    void ignoresOtherValidPaymentEventTypesOnTheSharedTopic() {
        String fundingInitiated = validEventJson()
                .replace("PaymentSucceeded", "FundingInitiated");

        consumer.consume(fundingInitiated);

        verify(fundingService, never()).secure(any());
    }

    @Test
    void rejectsEnvelopeAndPayloadMetadataThatDisagree() {
        String inconsistent = validEventJson().replaceFirst(
                CORRELATION_ID.toString(),
                "019c0000-0000-7000-8000-000000000099");

        assertThatThrownBy(() -> consumer.consume(inconsistent))
                .isInstanceOf(InvalidPaymentEventException.class)
                .hasMessageContaining("does not match");
        verify(fundingService, never()).secure(any());
    }

    @Test
    void rejectsPaymentSucceededWithANonSucceededStatus() {
        String invalidStatus = validEventJson().replace("\"status\":\"SUCCEEDED\"", "\"status\":\"FAILED\"");

        assertThatThrownBy(() -> consumer.consume(invalidStatus))
                .isInstanceOf(InvalidPaymentEventException.class)
                .hasMessageContaining("status must be SUCCEEDED");
        verify(fundingService, never()).secure(any());
    }

    private FundingSecured fundingSecured() {
        return new FundingSecured(
                JOURNAL_ID,
                PAYMENT_ID,
                ESCROW_ID,
                100000,
                "NGN",
                UUID.fromString("019c0000-0000-7000-8000-000000000060"),
                UUID.fromString("019c0000-0000-7000-8000-000000000061"),
                OCCURRED_AT,
                false);
    }

    private String validEventJson() {
        return eventJson(OCCURRED_AT, OCCURRED_AT);
    }

    private String eventJson(Instant envelopeOccurredAt, Instant payloadOccurredAt) {
        return """
                {
                  "eventId":"%s",
                  "aggregateType":"Payment",
                  "aggregateId":"%s",
                  "eventType":"PaymentSucceeded",
                  "eventVersion":1,
                  "occurredAt":"%s",
                  "correlationId":"%s",
                  "payload":{
                    "eventType":"PaymentSucceeded",
                    "eventVersion":1,
                    "occurredAt":"%s",
                    "paymentId":"%s",
                    "escrowId":"%s",
                    "payerId":"%s",
                    "amountMinor":100000,
                    "currency":"NGN",
                    "provider":"SIMULATED",
                    "providerReference":"simulated-transaction-1001",
                    "status":"SUCCEEDED",
                    "aggregateVersion":1,
                    "correlationId":"%s"
                  }
                }
                """.formatted(
                EVENT_ID,
                PAYMENT_ID,
                envelopeOccurredAt,
                CORRELATION_ID,
                payloadOccurredAt,
                PAYMENT_ID,
                ESCROW_ID,
                PAYER_ID,
                CORRELATION_ID);
    }
}
