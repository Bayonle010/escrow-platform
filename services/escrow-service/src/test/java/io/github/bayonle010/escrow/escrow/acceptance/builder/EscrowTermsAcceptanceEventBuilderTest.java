package io.github.bayonle010.escrow.escrow.acceptance.builder;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class EscrowTermsAcceptanceEventBuilderTest {

    @Test
    void buildsVersionedTermsAcceptedEvent() {
        UUID escrowId = UUID.fromString("019c0000-0000-7000-8000-000000000020");
        UUID participantId = UUID.fromString("019c0000-0000-7000-8000-000000000002");
        UUID correlationId = UUID.fromString("019c0000-0000-7000-8000-000000000010");
        Instant acceptedAt = Instant.parse("2026-08-20T12:00:00Z");

        var event = new EscrowTermsAcceptanceEventBuilder(JsonMapper.builder().build()).build(
                escrowId, participantId, 1, 2, acceptedAt, correlationId);

        assertThat(event.getEventType()).isEqualTo("EscrowTermsAccepted");
        assertThat(event.getEventVersion()).isEqualTo(1);
        assertThat(event.getCorrelationId()).isEqualTo(correlationId);
        assertThat(event.getPayload().get("eventType").asString()).isEqualTo("EscrowTermsAccepted");
        assertThat(event.getPayload().get("eventVersion").asInt()).isEqualTo(1);
        assertThat(event.getPayload().get("occurredAt").asString()).isEqualTo(acceptedAt.toString());
        assertThat(event.getPayload().get("escrowId").asString()).isEqualTo(escrowId.toString());
        assertThat(event.getPayload().get("participantId").asString()).isEqualTo(participantId.toString());
        assertThat(event.getPayload().get("termsVersion").asInt()).isEqualTo(1);
        assertThat(event.getPayload().get("state").asString()).isEqualTo("AWAITING_FUNDING");
        assertThat(event.getPayload().get("aggregateVersion").asLong()).isEqualTo(2L);
    }
}
