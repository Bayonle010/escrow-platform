package io.github.bayonle010.escrow.ledger.messaging.outbox;

import java.util.UUID;

import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class LedgerOutboxEventSerializer {

    private final ObjectMapper objectMapper;

    public LedgerOutboxEventSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SerializedOutboxEvent serialize(LedgerOutboxEvent event) throws JacksonException {
        JsonNode payload = objectMapper.readTree(event.payload());
        JsonNode escrowIdNode = payload.get("escrowId");
        if (escrowIdNode == null || !escrowIdNode.isTextual()) {
            throw new IllegalArgumentException("The Ledger outbox payload must contain a textual escrowId.");
        }

        UUID payloadEscrowId;
        try {
            payloadEscrowId = UUID.fromString(escrowIdNode.asText());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("The Ledger outbox payload escrowId must be a UUID.", exception);
        }
        if (!event.partitionKey().equals(payloadEscrowId)) {
            throw new IllegalArgumentException("The Ledger outbox partition key must match payload escrowId.");
        }

        var envelope = new LedgerEventEnvelope(
                event.eventId(),
                event.aggregateType(),
                event.aggregateId(),
                event.eventType(),
                event.eventVersion(),
                event.occurredAt(),
                event.correlationId(),
                event.causationId(),
                payload);
        return new SerializedOutboxEvent(
                event.partitionKey().toString(),
                objectMapper.writeValueAsString(envelope));
    }
}
