package io.github.bayonle010.escrow.identity.registration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import io.github.bayonle010.escrow.identity.registration.domain.UserRegistration;
import io.github.bayonle010.escrow.identity.registration.domain.UserStatus;
import io.github.bayonle010.escrow.identity.shared.exception.InvalidPasswordException;

class RegistrationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-20T12:00:00Z");
    private static final UUID CORRELATION_ID =
            UUID.fromString("019c0000-0000-7000-8000-000000000010");

    private final RegistrationPersistenceService persistenceService = mock(RegistrationPersistenceService.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final RegistrationService service = new RegistrationService(
            persistenceService,
            passwordEncoder,
            clock);

    @Test
    void normalizesEmailAndPersistsOnlyThePasswordHash() {
        when(passwordEncoder.encode("A-secure-password1!")).thenReturn("bcrypt-hash");
        UUID generatedUserId = UUID.fromString("019c0000-0000-7000-8000-000000000001");
        when(persistenceService.save(org.mockito.ArgumentMatchers.any(UserRegistration.class)))
                .thenReturn(generatedUserId);

        var result = service.register(" Alice@Example.COM ", "A-secure-password1!", CORRELATION_ID);

        ArgumentCaptor<UserRegistration> registration = ArgumentCaptor.forClass(UserRegistration.class);
        verify(persistenceService).save(registration.capture());
        assertThat(registration.getValue().normalizedEmail()).isEqualTo("alice@example.com");
        assertThat(registration.getValue().passwordHash()).isEqualTo("bcrypt-hash");
        assertThat(registration.getValue().passwordHash()).doesNotContain("A-secure-password1!");
        assertThat(registration.getValue().status()).isEqualTo(UserStatus.PENDING_VERIFICATION);
        assertThat(registration.getValue().createdAt()).isEqualTo(NOW);
        assertThat(registration.getValue().correlationId()).isEqualTo(CORRELATION_ID);
        assertThat(result.userId()).isEqualTo(generatedUserId);
        assertThat(result.email()).isEqualTo("alice@example.com");
    }

    @Test
    void rejectsPasswordsLongerThanBcryptsUtf8LimitBeforeHashing() {
        String password = "\u00e9".repeat(37);

        assertThatThrownBy(() -> service.register("alice@example.com", password, CORRELATION_ID))
                .isInstanceOf(InvalidPasswordException.class)
                .hasMessage("Password must be at most 72 UTF-8 bytes.");

        verifyNoInteractions(persistenceService, passwordEncoder);
    }
}
