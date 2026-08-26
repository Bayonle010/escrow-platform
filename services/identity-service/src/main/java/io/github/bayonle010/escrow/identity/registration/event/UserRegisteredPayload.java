package io.github.bayonle010.escrow.identity.registration.event;

import java.time.Instant;
import java.util.UUID;

import io.github.bayonle010.escrow.identity.registration.domain.UserStatus;

public record UserRegisteredPayload(
        String eventType,
        int eventVersion,
        Instant occurredAt,
        UUID userId,
        String email,
        UserStatus status) {
}
