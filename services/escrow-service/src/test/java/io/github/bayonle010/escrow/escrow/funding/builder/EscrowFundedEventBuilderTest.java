package io.github.bayonle010.escrow.escrow.funding.builder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.bayonle010.escrow.escrow.creation.entity.EscrowEntity;
import io.github.bayonle010.escrow.escrow.funding.domain.EscrowFundingSecuredEvent;
import tools.jackson.databind.json.JsonMapper;

class EscrowFundedEventBuilderTest {

    @Test
    void buildsATraceableEscrowFundedOutboxEventAtDatabasePrecision() {
        UUID eventId = UUID.fromString("01a06bee-2621-7603-812c-4bad587d6956");
        UUID journalId = UUID.fromString("01a06bee-25bd-7d14-96bb-87413b42230f");
        UUID paymentId = UUID.fromString("01a069da-eddf-7820-be8d-6c60a54c3259");
        UUID escrowId = UUID.fromString("01a069d1-82fc-70f5-a5d4-21f81e9f7c4c");
        UUID correlationId = UUID.fromString("01a069dd-45ee-752c-806f-51b8ef43497c");
        UUID causationId = UUID.fromString("01a069dd-467e-74c2-9435-c233b0f0f3a0");
        Instant eventTime = Instant.parse("2026-09-04T10:19:24.448020457Z");
        Instant persistedTime = Instant.parse("2026-09-04T10:19:24.448020Z");
        EscrowEntity escrow = mock(EscrowEntity.class);
        when(escrow.getEscrowId()).thenReturn(escrowId);
        when(escrow.getVersion()).thenReturn(2L);
        var cause = new EscrowFundingSecuredEvent(
                eventId, 1, eventTime, journalId, paymentId, escrowId,
                100000, "NGN", correlationId, causationId);

        var event = new EscrowFundedEventBuilder(JsonMapper.builder().build())
                .build(escrow, cause, eventTime);

        assertThat(event.getAggregateId()).isEqualTo(escrowId);
        assertThat(event.getEventType()).isEqualTo("EscrowFunded");
        assertThat(event.getCorrelationId()).isEqualTo(correlationId);
        assertThat(event.getCausationId()).isEqualTo(eventId);
        assertThat(event.getOccurredAt()).isEqualTo(persistedTime);
        assertThat(event.getPayload().get("occurredAt").asString()).isEqualTo(persistedTime.toString());
        assertThat(event.getPayload().get("aggregateVersion").asLong()).isEqualTo(2);
        assertThat(event.getPayload().get("state").asString()).isEqualTo("FUNDED");
    }
}
