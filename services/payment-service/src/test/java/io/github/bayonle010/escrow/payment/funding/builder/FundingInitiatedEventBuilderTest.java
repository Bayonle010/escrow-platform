package io.github.bayonle010.escrow.payment.funding.builder;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.bayonle010.escrow.payment.funding.domain.PaymentStatus;
import io.github.bayonle010.escrow.payment.funding.entity.OutboxStatus;
import io.github.bayonle010.escrow.payment.funding.entity.PaymentEntity;
import tools.jackson.databind.json.JsonMapper;

class FundingInitiatedEventBuilderTest {

    @Test
    void buildsTheVersionedFundingEvent() throws Exception {
        UUID paymentId = UUID.fromString("019c0000-0000-7000-8000-000000000030");
        UUID escrowId = UUID.fromString("019c0000-0000-7000-8000-000000000020");
        UUID buyerId = UUID.fromString("019c0000-0000-7000-8000-000000000001");
        UUID correlationId = UUID.fromString("019c0000-0000-7000-8000-000000000010");
        Instant now = Instant.parse("2026-08-20T12:00:00.123456789Z");
        PaymentEntity payment = PaymentEntity.builder()
                .paymentId(paymentId)
                .escrowId(escrowId)
                .payerId(buyerId)
                .amountMinor(100000)
                .currency("NGN")
                .provider("SIMULATED")
                .status(PaymentStatus.PROCESSING)
                .createdAt(now)
                .build();

        var jsonMapper = JsonMapper.builder().build();
        var event = new FundingInitiatedEventBuilder(jsonMapper)
                .build(payment, correlationId);
        var payload = jsonMapper.readTree(event.getPayload());

        assertThat(event.getAggregateId()).isEqualTo(paymentId);
        assertThat(event.getEventType()).isEqualTo("FundingInitiated");
        assertThat(event.getEventVersion()).isEqualTo(1);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(payload.get("eventType").asString()).isEqualTo("FundingInitiated");
        assertThat(payload.get("eventVersion").asInt()).isEqualTo(1);
        assertThat(payload.get("paymentId").asString()).isEqualTo(paymentId.toString());
        assertThat(payload.get("escrowId").asString()).isEqualTo(escrowId.toString());
        assertThat(payload.get("payerId").asString()).isEqualTo(buyerId.toString());
        assertThat(payload.get("amountMinor").asLong()).isEqualTo(100000L);
        assertThat(payload.get("status").asString()).isEqualTo("PROCESSING");
        assertThat(event.getOccurredAt()).isEqualTo(Instant.parse("2026-08-20T12:00:00.123456Z"));
        assertThat(payload.get("occurredAt").asString()).isEqualTo("2026-08-20T12:00:00.123456Z");
        assertThat(payload.get("correlationId").asString()).isEqualTo(correlationId.toString());
    }
}
