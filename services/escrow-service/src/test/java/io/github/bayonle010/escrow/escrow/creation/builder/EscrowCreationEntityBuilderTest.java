package io.github.bayonle010.escrow.escrow.creation.builder;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.bayonle010.escrow.escrow.creation.domain.EscrowCreation;
import io.github.bayonle010.escrow.escrow.creation.domain.EscrowState;
import tools.jackson.databind.json.JsonMapper;

class EscrowCreationEntityBuilderTest {

    @Test
    void buildsVersionedTermsAndDocumentedOutboxPayload() {
        UUID buyerId = UUID.fromString("019c0000-0000-7000-8000-000000000001");
        UUID sellerId = UUID.fromString("019c0000-0000-7000-8000-000000000002");
        UUID escrowId = UUID.fromString("019c0000-0000-7000-8000-000000000020");
        UUID correlationId = UUID.fromString("019c0000-0000-7000-8000-000000000010");
        EscrowCreation creation = new EscrowCreation(
                buyerId,
                sellerId,
                buyerId,
                100000,
                "NGN",
                "Professional camera",
                "GOODS",
                Instant.parse("2026-09-30T12:00:00Z"),
                7,
                "Release after accepted delivery",
                "Refund if delivery misses the deadline",
                1,
                EscrowState.AWAITING_COUNTERPARTY,
                Instant.parse("2026-08-20T12:00:00Z"),
                correlationId);

        var builder = new EscrowCreationEntityBuilder(JsonMapper.builder().build());
        var terms = builder.buildTerms(creation, escrowId);
        var outboxEvent = builder.buildOutboxEvent(creation, escrowId);

        assertThat(terms.getEscrowId()).isEqualTo(escrowId);
        assertThat(terms.getTermsVersion()).isEqualTo(1);
        assertThat(terms.getCreatedBy()).isEqualTo(buyerId);
        assertThat(outboxEvent.getAggregateId()).isEqualTo(escrowId);
        assertThat(outboxEvent.getCorrelationId()).isEqualTo(correlationId);
        assertThat(outboxEvent.getPayload().get("eventType").asString()).isEqualTo("EscrowCreated");
        assertThat(outboxEvent.getPayload().get("eventVersion").asInt()).isEqualTo(1);
        assertThat(outboxEvent.getPayload().get("occurredAt").asString())
                .isEqualTo(creation.createdAt().toString());
        assertThat(outboxEvent.getPayload().get("escrowId").asString()).isEqualTo(escrowId.toString());
        assertThat(outboxEvent.getPayload().get("buyerId").asString()).isEqualTo(buyerId.toString());
        assertThat(outboxEvent.getPayload().get("sellerId").asString()).isEqualTo(sellerId.toString());
        assertThat(outboxEvent.getPayload().get("amountMinor").asLong()).isEqualTo(100000L);
        assertThat(outboxEvent.getPayload().get("currency").asString()).isEqualTo("NGN");
        assertThat(outboxEvent.getPayload().get("termsVersion").asInt()).isEqualTo(1);
        assertThat(outboxEvent.getPayload().get("state").asString()).isEqualTo("AWAITING_COUNTERPARTY");
        assertThat(outboxEvent.getPayload().get("aggregateVersion").asInt()).isEqualTo(1);
        assertThat(outboxEvent.getPayload().has("correlationId")).isFalse();
    }
}
