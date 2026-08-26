package io.github.bayonle010.escrow.escrow.acceptance.builder;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class EscrowTermsAcceptanceEventBuilderTest {

    @Test
    void buildsVersionedTermsAcceptedEvent() {
        UUID escrowId = UUID.fromString("019c0000-0000-7000-8000-000000000020");
        UUID participantId = UUID.fromString("019c0000-0000-7000-8000-000000000002");
        UUID correlationId = UUID.fromString("019c0000-0000-7000-8000-000000000010");
        Instant acceptedAt = Instant.parse("2026-08-20T12:00:00Z");

        var event = new EscrowTermsAcceptanceEventBuilder().build(
                escrowId, participantId, 1, 2, acceptedAt, correlationId);

        assertThat(event.getEventType()).isEqualTo("EscrowTermsAccepted");
        assertThat(event.getEventVersion()).isEqualTo(1);
        assertThat(event.getCorrelationId()).isEqualTo(correlationId);
        assertThat(event.getPayload())
                .containsEntry("eventType", "EscrowTermsAccepted")
                .containsEntry("escrowId", escrowId.toString())
                .containsEntry("participantId", participantId.toString())
                .containsEntry("termsVersion", 1)
                .containsEntry("state", "AWAITING_FUNDING")
                .containsEntry("aggregateVersion", 2L);
    }
}
