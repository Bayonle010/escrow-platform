package io.github.bayonle010.escrow.ledger.messaging.outbox;

import java.time.Instant;
import java.util.UUID;

import tools.jackson.databind.JsonNode;

public record LedgerEventEnvelope(
        UUID eventId,
        String aggregateType,
        UUID aggregateId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        UUID correlationId,
        UUID causationId,
        JsonNode payload) {
}
