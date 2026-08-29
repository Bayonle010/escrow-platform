package io.github.bayonle010.escrow.payment.funding.builder;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.bayonle010.escrow.payment.funding.domain.PaymentStatus;
import io.github.bayonle010.escrow.payment.funding.entity.PaymentEntity;
import io.github.bayonle010.escrow.payment.funding.entity.OutboxStatus;
import tools.jackson.databind.json.JsonMapper;

class PaymentSucceededEventBuilderTest {

    @Test
    void buildsTheStablePaymentSucceededPayload() throws Exception {
        UUID paymentId = UUID.fromString("019c0000-0000-7000-8000-000000000030");
        UUID escrowId = UUID.fromString("019c0000-0000-7000-8000-000000000020");
        UUID payerId = UUID.fromString("019c0000-0000-7000-8000-000000000001");
        UUID correlationId = UUID.fromString("019c0000-0000-7000-8000-000000000010");
        Instant confirmedAt = Instant.parse("2026-08-28T00:00:00Z");
        PaymentEntity payment = PaymentEntity.builder()
                .paymentId(paymentId)
                .escrowId(escrowId)
                .payerId(payerId)
                .amountMinor(100000)
                .currency("NGN")
                .provider("SIMULATED")
                .providerReference("simulated-transaction-1001")
                .status(PaymentStatus.SUCCEEDED)
                .updatedAt(confirmedAt)
                .version(1)
                .build();

        var jsonMapper = JsonMapper.builder().build();
        var event = new PaymentSucceededEventBuilder(jsonMapper)
                .build(payment, correlationId);
        var payload = jsonMapper.readTree(event.getPayload());

        assertThat(event.getEventType()).isEqualTo("PaymentSucceeded");
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getAggregateId()).isEqualTo(paymentId);
        assertThat(payload.get("eventVersion").asInt()).isEqualTo(1);
        assertThat(payload.get("escrowId").asString()).isEqualTo(escrowId.toString());
        assertThat(payload.get("providerReference").asString())
                .isEqualTo("simulated-transaction-1001");
        assertThat(payload.get("status").asString()).isEqualTo("SUCCEEDED");
        assertThat(payload.get("aggregateVersion").asLong()).isEqualTo(1);
        assertThat(payload.get("correlationId").asString())
                .isEqualTo(correlationId.toString());
    }
}
