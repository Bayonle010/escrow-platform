package io.github.bayonle010.escrow.ledger.messaging.outbox;

import java.time.Instant;
import java.util.UUID;

public record LedgerOutboxEvent(
        UUID eventId,
        UUID aggregateId,
        String aggregateType,
        String eventType,
        int eventVersion,
        UUID partitionKey,
        UUID correlationId,
        UUID causationId,
        String payload,
        Instant occurredAt,
        int attempts) {
}
