package io.github.bayonle010.escrow.identity.registration.builder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import io.github.bayonle010.escrow.identity.registration.domain.UserRegistration;
import io.github.bayonle010.escrow.identity.registration.entity.OutboxEventEntity;
import io.github.bayonle010.escrow.identity.registration.entity.OutboxStatus;
import io.github.bayonle010.escrow.identity.registration.entity.UserEntity;

@Component
public class RegistrationEntityBuilder {

    public UserEntity buildUser(UserRegistration registration) {
        return UserEntity.builder()
                .email(registration.email())
                .normalizedEmail(registration.normalizedEmail())
                .passwordHash(registration.passwordHash())
                .status(registration.status())
                .createdAt(registration.createdAt())
                .build();
    }

    public OutboxEventEntity buildOutboxEvent(UserRegistration registration, UUID userId) {
        return OutboxEventEntity.builder()
                .aggregateId(userId)
                .aggregateType("User")
                .eventType("UserRegistered")
                .eventVersion(1)
                .correlationId(registration.correlationId())
                .payload(buildPayload(registration, userId))
                .occurredAt(registration.createdAt())
                .status(OutboxStatus.PENDING)
                .attempts(0)
                .nextAttemptAt(registration.createdAt())
                .build();
    }

    private Map<String, Object> buildPayload(UserRegistration registration, UUID userId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", "UserRegistered");
        payload.put("eventVersion", 1);
        payload.put("occurredAt", registration.createdAt().toString());
        payload.put("userId", userId.toString());
        payload.put("email", registration.email());
        payload.put("status", registration.status().name());
        return payload;
    }
}
