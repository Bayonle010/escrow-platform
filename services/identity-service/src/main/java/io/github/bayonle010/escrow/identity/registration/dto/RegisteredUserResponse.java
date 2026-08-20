package io.github.bayonle010.escrow.identity.registration.dto;

import java.time.Instant;
import java.util.UUID;

import io.github.bayonle010.escrow.identity.registration.domain.RegisteredUser;

public record RegisteredUserResponse(UUID id, String email, String status, Instant createdAt) {

    public static RegisteredUserResponse from(RegisteredUser user) {
        return new RegisteredUserResponse(
                user.userId(),
                user.email(),
                user.status().name(),
                user.createdAt());
    }
}
