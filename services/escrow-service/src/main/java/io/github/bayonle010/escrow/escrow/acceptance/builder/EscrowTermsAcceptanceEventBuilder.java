package io.github.bayonle010.escrow.escrow.acceptance.builder;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

import io.github.bayonle010.escrow.escrow.acceptance.event.EscrowTermsAcceptedPayload;
import io.github.bayonle010.escrow.escrow.creation.domain.EscrowState;
import io.github.bayonle010.escrow.escrow.creation.entity.OutboxEventEntity;
import io.github.bayonle010.escrow.escrow.creation.entity.OutboxStatus;
import tools.jackson.databind.ObjectMapper;

@Component
public class EscrowTermsAcceptanceEventBuilder {

    private static final String EVENT_TYPE = "EscrowTermsAccepted";
    private static final int EVENT_VERSION = 1;

    private final ObjectMapper objectMapper;

    public EscrowTermsAcceptanceEventBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

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
                .eventType(EVENT_TYPE)
                .eventVersion(EVENT_VERSION)
                .correlationId(correlationId)
                .payload(objectMapper.valueToTree(new EscrowTermsAcceptedPayload(
                        EVENT_TYPE,
                        EVENT_VERSION,
                        acceptedAt,
                        escrowId,
                        participantId,
                        termsVersion,
                        EscrowState.AWAITING_FUNDING,
                        aggregateVersion)))
                .occurredAt(acceptedAt)
                .status(OutboxStatus.PENDING)
                .attempts(0)
                .nextAttemptAt(acceptedAt)
                .build();
    }

}
