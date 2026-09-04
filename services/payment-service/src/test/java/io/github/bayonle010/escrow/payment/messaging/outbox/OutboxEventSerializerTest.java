package io.github.bayonle010.escrow.payment.messaging.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.bayonle010.escrow.payment.funding.entity.OutboxEventEntity;
import io.github.bayonle010.escrow.payment.funding.entity.OutboxStatus;
import tools.jackson.databind.json.JsonMapper;

class OutboxEventSerializerTest {

    private static final UUID EVENT_ID = UUID.fromString("019c0000-0000-7000-8000-000000000040");
    private static final UUID PAYMENT_ID = UUID.fromString("019c0000-0000-7000-8000-000000000030");
    private static final UUID ESCROW_ID = UUID.fromString("019c0000-0000-7000-8000-000000000020");
    private static final UUID CORRELATION_ID = UUID.fromString("019c0000-0000-7000-8000-000000000010");
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-28T00:00:00Z");

    private final JsonMapper objectMapper = JsonMapper.builder().build();
    private final OutboxEventSerializer serializer = new OutboxEventSerializer(objectMapper);

    @Test
    void wrapsTheStoredPayloadAndUsesEscrowIdAsThePartitionKey() throws Exception {
        SerializedOutboxEvent serialized = serializer.serialize(event(
                "{\"escrowId\":\"" + ESCROW_ID + "\",\"amountMinor\":100000}"));

        var envelope = objectMapper.readTree(serialized.value());
        assertThat(serialized.partitionKey()).isEqualTo(ESCROW_ID.toString());
        assertThat(envelope.get("eventId").asString()).isEqualTo(EVENT_ID.toString());
        assertThat(envelope.get("aggregateId").asString()).isEqualTo(PAYMENT_ID.toString());
        assertThat(envelope.get("eventType").asString()).isEqualTo("PaymentSucceeded");
        assertThat(envelope.get("eventVersion").asInt()).isEqualTo(1);
        assertThat(envelope.get("payload").get("amountMinor").asLong()).isEqualTo(100000);
    }

    @Test
    void rejectsPayloadsWithoutAUsableEscrowPartitionKey() {
        assertThatThrownBy(() -> serializer.serialize(event("{\"amountMinor\":100000}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("escrowId");
    }

    private OutboxEventEntity event(String payload) {
        return OutboxEventEntity.builder()
                .eventId(EVENT_ID)
                .aggregateId(PAYMENT_ID)
                .aggregateType("Payment")
                .eventType("PaymentSucceeded")
                .eventVersion(1)
                .correlationId(CORRELATION_ID)
                .payload(payload)
                .occurredAt(OCCURRED_AT)
                .status(OutboxStatus.PENDING)
                .attempts(0)
                .nextAttemptAt(OCCURRED_AT)
                .build();
    }
}
