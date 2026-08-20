package io.github.bayonle010.escrow.identity.registration.dto;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import io.github.bayonle010.escrow.identity.registration.domain.RegisteredUser;

public record RegisteredUserResponse(
        @Schema(example = "019c0000-0000-7000-8000-000000000001") UUID id,
        @Schema(example = "alice@example.com") String email,
        @Schema(example = "PENDING_VERIFICATION") String status,
        @Schema(example = "2026-08-20T12:00:00Z") Instant createdAt) {

    public static RegisteredUserResponse from(RegisteredUser user) {
        return new RegisteredUserResponse(
                user.userId(),
                user.email(),
                user.status().name(),
                user.createdAt());
    }
}
