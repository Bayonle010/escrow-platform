package io.github.bayonle010.escrow.escrow.acceptance.builder;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import io.github.bayonle010.escrow.escrow.creation.domain.EscrowState;
import io.github.bayonle010.escrow.escrow.creation.entity.OutboxEventEntity;
import io.github.bayonle010.escrow.escrow.creation.entity.OutboxStatus;

@Component
public class EscrowTermsAcceptanceEventBuilder {

    public OutboxEventEntity build(
            UUID escrowId,
            UUID participantId,
            int termsVersion,
            long aggregateVersion,
            Instant acceptedAt,
            UUID correlationId) {
        return OutboxEventEntity.builder()
                .aggregateId(escrowId)
                .aggregateType("Escrow")
                .eventType("EscrowTermsAccepted")
                .eventVersion(1)
                .correlationId(correlationId)
                .payload(payload(
                        escrowId,
                        participantId,
                        termsVersion,
                        aggregateVersion,
                        acceptedAt))
                .occurredAt(acceptedAt)
                .status(OutboxStatus.PENDING)
                .attempts(0)
                .nextAttemptAt(acceptedAt)
                .build();
    }

    private Map<String, Object> payload(
            UUID escrowId,
            UUID participantId,
            int termsVersion,
            long aggregateVersion,
            Instant acceptedAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", "EscrowTermsAccepted");
        payload.put("eventVersion", 1);
        payload.put("occurredAt", acceptedAt.toString());
        payload.put("escrowId", escrowId.toString());
        payload.put("participantId", participantId.toString());
        payload.put("termsVersion", termsVersion);
        payload.put("state", EscrowState.AWAITING_FUNDING.name());
        payload.put("aggregateVersion", aggregateVersion);
        return payload;
    }
}
