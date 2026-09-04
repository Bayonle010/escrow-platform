package io.github.bayonle010.escrow.payment.messaging.outbox;

import java.util.UUID;

import org.springframework.stereotype.Component;

import io.github.bayonle010.escrow.payment.funding.entity.OutboxEventEntity;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class OutboxEventSerializer {

    private final ObjectMapper objectMapper;

    public OutboxEventSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SerializedOutboxEvent serialize(OutboxEventEntity event) throws JacksonException {
        JsonNode payload = objectMapper.readTree(event.getPayload());
        JsonNode escrowIdNode = payload.get("escrowId");
        if (escrowIdNode == null || !escrowIdNode.isTextual()) {
            throw new IllegalArgumentException("The outbox payload must contain a textual escrowId.");
        }

        UUID escrowId;
        try {
            escrowId = UUID.fromString(escrowIdNode.asText());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("The outbox payload escrowId must be a UUID.", exception);
        }

        PaymentEventEnvelope envelope = new PaymentEventEnvelope(
                event.getEventId(),
                event.getAggregateType(),
                event.getAggregateId(),
                event.getEventType(),
                event.getEventVersion(),
                event.getOccurredAt(),
                event.getCorrelationId(),
                payload);
        return new SerializedOutboxEvent(
                escrowId.toString(),
                objectMapper.writeValueAsString(envelope));
    }
}
