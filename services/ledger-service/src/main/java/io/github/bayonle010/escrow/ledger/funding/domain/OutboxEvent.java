package io.github.bayonle010.escrow.ledger.funding.domain;

import java.time.Instant;
import java.util.UUID;

import tools.jackson.databind.JsonNode;

public record OutboxEvent(
        UUID eventId,
        UUID aggregateId,
        String aggregateType,
        String eventType,
        int eventVersion,
        UUID partitionKey,
        UUID correlationId,
        UUID causationId,
        JsonNode payload,
        Instant occurredAt) {
}
