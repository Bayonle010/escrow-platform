package io.github.bayonle010.escrow.identity.registration.builder;

import java.util.UUID;

import org.springframework.stereotype.Component;

import io.github.bayonle010.escrow.identity.registration.domain.UserRegistration;
import io.github.bayonle010.escrow.identity.registration.entity.OutboxEventEntity;
import io.github.bayonle010.escrow.identity.registration.entity.OutboxStatus;
import io.github.bayonle010.escrow.identity.registration.entity.UserEntity;
import io.github.bayonle010.escrow.identity.registration.event.UserRegisteredPayload;
import tools.jackson.databind.ObjectMapper;

@Component
public class RegistrationEntityBuilder {

    private static final String EVENT_TYPE = "UserRegistered";
    private static final int EVENT_VERSION = 1;

    private final ObjectMapper objectMapper;

    public RegistrationEntityBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

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
                .eventType(EVENT_TYPE)
                .eventVersion(EVENT_VERSION)
                .correlationId(registration.correlationId())
                .payload(objectMapper.valueToTree(new UserRegisteredPayload(
                        EVENT_TYPE,
                        EVENT_VERSION,
                        registration.createdAt(),
                        userId,
                        registration.email(),
                        registration.status())))
                .occurredAt(registration.createdAt())
                .status(OutboxStatus.PENDING)
                .attempts(0)
                .nextAttemptAt(registration.createdAt())
                .build();
    }

}
