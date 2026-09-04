package io.github.bayonle010.escrow.ledger.messaging.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class LedgerOutboxEventSerializerTest {

    private static final UUID EVENT_ID = UUID.fromString("019c0000-0000-7000-8000-000000000060");
    private static final UUID JOURNAL_ID = UUID.fromString("019c0000-0000-7000-8000-000000000050");
    private static final UUID ESCROW_ID = UUID.fromString("019c0000-0000-7000-8000-000000000020");
    private static final UUID CORRELATION_ID = UUID.fromString("019c0000-0000-7000-8000-000000000010");
    private static final UUID CAUSATION_ID = UUID.fromString("019c0000-0000-7000-8000-000000000040");
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-28T00:00:00Z");

    private final JsonMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
    private final LedgerOutboxEventSerializer serializer = new LedgerOutboxEventSerializer(objectMapper);

    @Test
    void wrapsTheStoredPayloadAndUsesEscrowIdAsThePartitionKey() throws Exception {
        SerializedOutboxEvent serialized = serializer.serialize(event(
                ESCROW_ID,
                "{\"escrowId\":\"" + ESCROW_ID + "\",\"amountMinor\":100000}"));

        var envelope = objectMapper.readTree(serialized.value());
        assertThat(serialized.partitionKey()).isEqualTo(ESCROW_ID.toString());
        assertThat(envelope.get("eventId").asString()).isEqualTo(EVENT_ID.toString());
        assertThat(envelope.get("aggregateType").asString()).isEqualTo("LedgerJournal");
        assertThat(envelope.get("aggregateId").asString()).isEqualTo(JOURNAL_ID.toString());
        assertThat(envelope.get("eventType").asString()).isEqualTo("EscrowFundingSecured");
        assertThat(envelope.get("eventVersion").asInt()).isEqualTo(1);
        assertThat(envelope.get("correlationId").asString()).isEqualTo(CORRELATION_ID.toString());
        assertThat(envelope.get("causationId").asString()).isEqualTo(CAUSATION_ID.toString());
        assertThat(envelope.get("payload").get("amountMinor").asLong()).isEqualTo(100000);
    }

    @Test
    void rejectsAPartitionKeyThatDoesNotMatchThePayloadEscrow() {
        UUID anotherEscrowId = UUID.fromString("019c0000-0000-7000-8000-000000000099");

        assertThatThrownBy(() -> serializer.serialize(event(
                anotherEscrowId,
                "{\"escrowId\":\"" + ESCROW_ID + "\"}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("partition key");
    }

    @Test
    void rejectsPayloadsWithoutAUsableEscrowPartitionKey() {
        assertThatThrownBy(() -> serializer.serialize(event(ESCROW_ID, "{\"amountMinor\":100000}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("escrowId");
    }

    private LedgerOutboxEvent event(UUID partitionKey, String payload) {
        return new LedgerOutboxEvent(
                EVENT_ID,
                JOURNAL_ID,
                "LedgerJournal",
                "EscrowFundingSecured",
                1,
                partitionKey,
                CORRELATION_ID,
                CAUSATION_ID,
                payload,
                OCCURRED_AT,
                0);
    }
}
