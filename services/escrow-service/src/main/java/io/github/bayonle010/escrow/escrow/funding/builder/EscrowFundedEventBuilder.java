package io.github.bayonle010.escrow.escrow.funding.builder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import io.github.bayonle010.escrow.escrow.creation.domain.EscrowState;
import io.github.bayonle010.escrow.escrow.creation.entity.EscrowEntity;
import io.github.bayonle010.escrow.escrow.creation.entity.OutboxEventEntity;
import io.github.bayonle010.escrow.escrow.creation.entity.OutboxStatus;
import io.github.bayonle010.escrow.escrow.funding.domain.EscrowFundingSecuredEvent;
import io.github.bayonle010.escrow.escrow.funding.event.EscrowFundedPayload;
import tools.jackson.databind.ObjectMapper;

@Component
public class EscrowFundedEventBuilder {

    private static final String EVENT_TYPE = "EscrowFunded";
    private static final int EVENT_VERSION = 1;

    private final ObjectMapper objectMapper;

    public EscrowFundedEventBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public OutboxEventEntity build(
            EscrowEntity escrow,
            EscrowFundingSecuredEvent cause,
            Instant occurredAt) {
        Instant eventTime = occurredAt.truncatedTo(ChronoUnit.MICROS);
        return OutboxEventEntity.builder()
                .aggregateId(escrow.getEscrowId())
                .aggregateType("Escrow")
                .eventType(EVENT_TYPE)
                .eventVersion(EVENT_VERSION)
                .correlationId(cause.correlationId())
                .causationId(cause.eventId())
                .payload(objectMapper.valueToTree(new EscrowFundedPayload(
                        EVENT_TYPE,
                        EVENT_VERSION,
                        eventTime,
                        escrow.getEscrowId(),
                        cause.journalId(),
                        cause.paymentId(),
                        cause.amountMinor(),
                        cause.currency(),
                        EscrowState.FUNDED,
                        escrow.getVersion(),
                        cause.correlationId(),
                        cause.eventId())))
                .occurredAt(eventTime)
                .status(OutboxStatus.PENDING)
                .attempts(0)
                .nextAttemptAt(eventTime)
                .build();
    }
}
