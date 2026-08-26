package io.github.bayonle010.escrow.escrow.creation.builder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import io.github.bayonle010.escrow.escrow.creation.domain.EscrowCreation;
import io.github.bayonle010.escrow.escrow.creation.entity.EscrowEntity;
import io.github.bayonle010.escrow.escrow.creation.entity.EscrowTermsEntity;
import io.github.bayonle010.escrow.escrow.creation.entity.OutboxEventEntity;
import io.github.bayonle010.escrow.escrow.creation.entity.OutboxStatus;

@Component
public class EscrowCreationEntityBuilder {

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
                .eventType("EscrowCreated")
                .eventVersion(1)
                .correlationId(creation.correlationId())
                .payload(buildPayload(creation, escrowId))
                .occurredAt(creation.createdAt())
                .status(OutboxStatus.PENDING)
                .attempts(0)
                .nextAttemptAt(creation.createdAt())
                .build();
    }

    private Map<String, Object> buildPayload(EscrowCreation creation, UUID escrowId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", "EscrowCreated");
        payload.put("eventVersion", 1);
        payload.put("occurredAt", creation.createdAt().toString());
        payload.put("escrowId", escrowId.toString());
        payload.put("buyerId", creation.buyerId().toString());
        payload.put("sellerId", creation.sellerId().toString());
        payload.put("amountMinor", creation.amountMinor());
        payload.put("currency", creation.currency());
        payload.put("termsVersion", creation.termsVersion());
        payload.put("state", creation.state().name());
        payload.put("aggregateVersion", 1);
        return payload;
    }
}
