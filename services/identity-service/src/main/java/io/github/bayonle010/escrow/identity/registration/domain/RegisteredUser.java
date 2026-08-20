package io.github.bayonle010.escrow.identity.registration.domain;

import java.time.Instant;
import java.util.UUID;

public record RegisteredUser(UUID userId, String email, UserStatus status, Instant createdAt) {
}
