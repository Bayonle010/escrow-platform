package io.github.bayonle010.escrow.ledger.funding.builder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.bayonle010.escrow.ledger.funding.domain.PaymentSucceededEvent;
import io.github.bayonle010.escrow.ledger.funding.domain.PostedFunding;
import io.github.bayonle010.escrow.ledger.shared.UuidV7Generator;
import tools.jackson.databind.json.JsonMapper;

class EscrowFundingSecuredEventBuilderTest {

    @Test
    void buildsStableEscrowFundingSecuredOutboxEvent() {
        UUID outboxEventId = UUID.fromString("019c0000-0000-7000-8000-000000000060");
        UUID causeEventId = UUID.fromString("019c0000-0000-7000-8000-000000000040");
        UUID journalId = UUID.fromString("019c0000-0000-7000-8000-000000000050");
        UUID paymentId = UUID.fromString("019c0000-0000-7000-8000-000000000030");
        UUID escrowId = UUID.fromString("019c0000-0000-7000-8000-000000000020");
        UUID payerId = UUID.fromString("019c0000-0000-7000-8000-000000000001");
        UUID correlationId = UUID.fromString("019c0000-0000-7000-8000-000000000010");
        Instant securedAt = Instant.parse("2026-08-28T00:00:00.123456789Z");
        Instant persistedEventTime = Instant.parse("2026-08-28T00:00:00.123456Z");
        UuidV7Generator uuidGenerator = mock(UuidV7Generator.class);
        when(uuidGenerator.generate()).thenReturn(outboxEventId);
        var builder = new EscrowFundingSecuredEventBuilder(
                uuidGenerator,
                JsonMapper.builder().build());
        var funding = new PostedFunding(
                journalId,
                paymentId,
                escrowId,
                100000,
                "NGN",
                "SIMULATED",
                "provider-reference",
                UUID.randomUUID(),
                UUID.randomUUID(),
                securedAt);
        var cause = new PaymentSucceededEvent(
                causeEventId,
                1,
                securedAt.minusSeconds(1),
                paymentId,
                escrowId,
                payerId,
                100000,
                "NGN",
                "SIMULATED",
                "provider-reference",
                1,
                correlationId);

        var event = builder.build(funding, cause, securedAt);

        assertThat(event.eventId()).isEqualTo(outboxEventId);
        assertThat(event.aggregateId()).isEqualTo(journalId);
        assertThat(event.eventType()).isEqualTo("EscrowFundingSecured");
        assertThat(event.partitionKey()).isEqualTo(escrowId);
        assertThat(event.causationId()).isEqualTo(causeEventId);
        assertThat(event.occurredAt()).isEqualTo(persistedEventTime);
        assertThat(event.payload().get("occurredAt").asString())
                .isEqualTo(persistedEventTime.toString());
        assertThat(event.payload().get("journalId").asString()).isEqualTo(journalId.toString());
        assertThat(event.payload().get("amountMinor").asLong()).isEqualTo(100000);
        assertThat(event.payload().has("status")).isFalse();
        assertThat(event.payload().get("correlationId").asString())
                .isEqualTo(correlationId.toString());
    }
}
