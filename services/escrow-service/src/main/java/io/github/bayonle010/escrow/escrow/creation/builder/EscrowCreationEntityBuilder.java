package io.github.bayonle010.escrow.escrow.creation.builder;

import java.util.UUID;

import org.springframework.stereotype.Component;

import io.github.bayonle010.escrow.escrow.creation.domain.EscrowCreation;
import io.github.bayonle010.escrow.escrow.creation.entity.EscrowEntity;
import io.github.bayonle010.escrow.escrow.creation.entity.EscrowTermsEntity;
import io.github.bayonle010.escrow.escrow.creation.entity.OutboxEventEntity;
import io.github.bayonle010.escrow.escrow.creation.entity.OutboxStatus;
import io.github.bayonle010.escrow.escrow.creation.event.EscrowCreatedPayload;
import tools.jackson.databind.ObjectMapper;

@Component
public class EscrowCreationEntityBuilder {

    private static final String EVENT_TYPE = "EscrowCreated";
    private static final int EVENT_VERSION = 1;
    private static final int INITIAL_AGGREGATE_VERSION = 1;

    private final ObjectMapper objectMapper;

    public EscrowCreationEntityBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public EscrowEntity buildEscrow(EscrowCreation creation) {
        return EscrowEntity.builder()
                .buyerId(creation.buyerId())
                .sellerId(creation.sellerId())
                .currentTermsVersion(creation.termsVersion())
                .state(creation.state())
                .amountMinor(creation.amountMinor())
                .currency(creation.currency())
                .inspectionPeriodDays(creation.inspectionPeriodDays())
                .deliveryDeadline(creation.deliveryDeadline())
                .createdAt(creation.createdAt())
                .updatedAt(creation.createdAt())
                .build();
    }

    public EscrowTermsEntity buildTerms(EscrowCreation creation, UUID escrowId) {
        return EscrowTermsEntity.builder()
                .escrowId(escrowId)
                .termsVersion(creation.termsVersion())
                .amountMinor(creation.amountMinor())
                .currency(creation.currency())
                .description(creation.description())
                .category(creation.category())
                .deliveryDeadline(creation.deliveryDeadline())
                .inspectionPeriodDays(creation.inspectionPeriodDays())
                .releaseConditions(creation.releaseConditions())
                .refundConditions(creation.refundConditions())
                .createdAt(creation.createdAt())
                .createdBy(creation.createdBy())
                .build();
    }

    public OutboxEventEntity buildOutboxEvent(EscrowCreation creation, UUID escrowId) {
        return OutboxEventEntity.builder()
                .aggregateId(escrowId)
                .aggregateType("Escrow")
                .eventType(EVENT_TYPE)
                .eventVersion(EVENT_VERSION)
                .correlationId(creation.correlationId())
                .payload(objectMapper.valueToTree(new EscrowCreatedPayload(
                        EVENT_TYPE,
                        EVENT_VERSION,
                        creation.createdAt(),
                        escrowId,
                        creation.buyerId(),
                        creation.sellerId(),
                        creation.amountMinor(),
                        creation.currency(),
                        creation.termsVersion(),
                        creation.state(),
                        INITIAL_AGGREGATE_VERSION)))
                .occurredAt(creation.createdAt())
                .status(OutboxStatus.PENDING)
                .attempts(0)
                .nextAttemptAt(creation.createdAt())
                .build();
    }

}
