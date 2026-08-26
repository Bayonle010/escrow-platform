package io.github.bayonle010.escrow.payment.funding.builder;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

import io.github.bayonle010.escrow.payment.funding.entity.OutboxEventEntity;
import io.github.bayonle010.escrow.payment.funding.entity.OutboxStatus;
import io.github.bayonle010.escrow.payment.funding.entity.PaymentEntity;
import io.github.bayonle010.escrow.payment.funding.event.FundingInitiatedPayload;
import tools.jackson.databind.ObjectMapper;

@Component
public class FundingInitiatedEventBuilder {

    private static final String EVENT_TYPE = "FundingInitiated";
    private static final int EVENT_VERSION = 1;

    private final ObjectMapper objectMapper;

    public FundingInitiatedEventBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public OutboxEventEntity build(PaymentEntity payment, UUID correlationId) {
        Instant occurredAt = payment.getCreatedAt();
        FundingInitiatedPayload payload = new FundingInitiatedPayload(
                EVENT_TYPE,
                EVENT_VERSION,
                payment.getPaymentId(),
                payment.getEscrowId(),
                payment.getPayerId(),
                payment.getAmountMinor(),
                payment.getCurrency(),
                payment.getProvider(),
                payment.getStatus(),
                occurredAt,
                correlationId);

        return OutboxEventEntity.builder()
                .aggregateId(payment.getPaymentId())
                .aggregateType("Payment")
                .eventType(EVENT_TYPE)
                .eventVersion(EVENT_VERSION)
                .correlationId(correlationId)
                .payload(objectMapper.valueToTree(payload))
                .occurredAt(occurredAt)
                .status(OutboxStatus.PENDING)
                .attempts(0)
                .nextAttemptAt(occurredAt)
                .build();
    }
}
