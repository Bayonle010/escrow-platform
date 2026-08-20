package io.github.bayonle010.escrow.identity.registration.domain;

import java.time.Instant;

public record UserRegistration(
        String email,
        String normalizedEmail,
        String passwordHash,
        UserStatus status,
        Instant createdAt) {
}
