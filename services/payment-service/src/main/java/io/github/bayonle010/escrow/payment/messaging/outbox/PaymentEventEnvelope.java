package io.github.bayonle010.escrow.payment.messaging.outbox;

import java.time.Instant;
import java.util.UUID;

import tools.jackson.databind.JsonNode;

public record PaymentEventEnvelope(
        UUID eventId,
        String aggregateType,
        UUID aggregateId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        UUID correlationId,
        JsonNode payload) {
}
