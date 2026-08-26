package io.github.bayonle010.escrow.escrow.creation.builder;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.bayonle010.escrow.escrow.creation.domain.EscrowCreation;
import io.github.bayonle010.escrow.escrow.creation.domain.EscrowState;

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

        var builder = new EscrowCreationEntityBuilder();
        var terms = builder.buildTerms(creation, escrowId);
        var outboxEvent = builder.buildOutboxEvent(creation, escrowId);

        assertThat(terms.getEscrowId()).isEqualTo(escrowId);
        assertThat(terms.getTermsVersion()).isEqualTo(1);
        assertThat(terms.getCreatedBy()).isEqualTo(buyerId);
        assertThat(outboxEvent.getAggregateId()).isEqualTo(escrowId);
        assertThat(outboxEvent.getCorrelationId()).isEqualTo(correlationId);
        assertThat(outboxEvent.getPayload())
                .containsEntry("eventType", "EscrowCreated")
                .containsEntry("escrowId", escrowId.toString())
                .containsEntry("amountMinor", 100000L)
                .containsEntry("currency", "NGN")
                .containsEntry("termsVersion", 1)
                .containsEntry("state", "AWAITING_COUNTERPARTY")
                .containsEntry("aggregateVersion", 1)
                .doesNotContainKey("correlationId");
    }
}
