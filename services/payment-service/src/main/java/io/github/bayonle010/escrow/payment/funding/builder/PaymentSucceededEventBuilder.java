package io.github.bayonle010.escrow.payment.funding.builder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.stereotype.Component;

import io.github.bayonle010.escrow.payment.funding.entity.OutboxEventEntity;
import io.github.bayonle010.escrow.payment.funding.entity.OutboxStatus;
import io.github.bayonle010.escrow.payment.funding.entity.PaymentEntity;
import io.github.bayonle010.escrow.payment.funding.event.PaymentSucceededPayload;
import tools.jackson.databind.ObjectMapper;

@Component
public class PaymentSucceededEventBuilder {

    private static final String EVENT_TYPE = "PaymentSucceeded";
    private static final int EVENT_VERSION = 1;

    private final ObjectMapper objectMapper;

    public PaymentSucceededEventBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public OutboxEventEntity build(PaymentEntity payment, UUID correlationId) {
        Instant occurredAt = payment.getUpdatedAt().truncatedTo(ChronoUnit.MICROS);
        PaymentSucceededPayload payload = new PaymentSucceededPayload(
                EVENT_TYPE,
                EVENT_VERSION,
                occurredAt,
                payment.getPaymentId(),
                payment.getEscrowId(),
                payment.getPayerId(),
                payment.getAmountMinor(),
                payment.getCurrency(),
                payment.getProvider(),
                payment.getProviderReference(),
                payment.getStatus(),
                payment.getVersion(),
                correlationId);

        return OutboxEventEntity.builder()
                .aggregateId(payment.getPaymentId())
                .aggregateType("Payment")
                .eventType(EVENT_TYPE)
                .eventVersion(EVENT_VERSION)
                .correlationId(correlationId)
                .payload(objectMapper.valueToTree(payload).toString())
                .occurredAt(occurredAt)
                .status(OutboxStatus.PENDING)
                .attempts(0)
                .nextAttemptAt(occurredAt)
                .build();
    }
}
