package io.github.bayonle010.escrow.identity.registration.builder;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.bayonle010.escrow.identity.registration.domain.UserRegistration;
import io.github.bayonle010.escrow.identity.registration.domain.UserStatus;

class RegistrationEntityBuilderTest {

    @Test
    void recordsCorrelationIdAsOutboxMetadata() {
        UUID correlationId = UUID.fromString("019c0000-0000-7000-8000-000000000010");
        UUID userId = UUID.fromString("019c0000-0000-7000-8000-000000000001");
        UserRegistration registration = new UserRegistration(
                "alice@example.com",
                "alice@example.com",
                "bcrypt-hash",
                UserStatus.PENDING_VERIFICATION,
                Instant.parse("2026-08-20T12:00:00Z"),
                correlationId);

        var outboxEvent = new RegistrationEntityBuilder().buildOutboxEvent(registration, userId);

        assertThat(outboxEvent.getCorrelationId()).isEqualTo(correlationId);
        assertThat(outboxEvent.getPayload()).doesNotContainKey("correlationId");
    }
}
