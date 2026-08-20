package io.github.bayonle010.escrow.identity.registration.service;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import io.github.bayonle010.escrow.identity.registration.domain.RegisteredUser;
import io.github.bayonle010.escrow.identity.registration.domain.UserRegistration;
import io.github.bayonle010.escrow.identity.registration.domain.UserStatus;
import io.github.bayonle010.escrow.identity.shared.exception.DuplicateEmailException;
import io.github.bayonle010.escrow.identity.shared.exception.InvalidPasswordException;

@Service
public class RegistrationService {

    private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;

    private final RegistrationPersistenceService persistenceService;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public RegistrationService(
            RegistrationPersistenceService persistenceService,
            PasswordEncoder passwordEncoder,
            Clock clock) {
        this.persistenceService = persistenceService;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    public RegisteredUser register(String email, String password, UUID correlationId) {
        validatePasswordLength(password);

        String normalizedEmail = email.strip().toLowerCase(Locale.ROOT);
        String passwordHash = passwordEncoder.encode(password);
        Instant createdAt = clock.instant();
        UserRegistration registration = new UserRegistration(
                normalizedEmail,
                normalizedEmail,
                passwordHash,
                UserStatus.PENDING_VERIFICATION,
                createdAt,
                correlationId);

        UUID userId;
        try {
            userId = persistenceService.save(registration);
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateEmail(exception)) {
                throw new DuplicateEmailException();
            }
            throw exception;
        }

        return new RegisteredUser(userId, normalizedEmail, registration.status(), createdAt);
    }

    private void validatePasswordLength(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_PASSWORD_BYTES) {
            throw new InvalidPasswordException("Password must be at most 72 UTF-8 bytes.");
        }
    }

    private boolean isDuplicateEmail(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof SQLException sqlException
                    && "23505".equals(sqlException.getSQLState())
                    && containsEmailConstraint(sqlException.getMessage())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private boolean containsEmailConstraint(String message) {
        return message != null
                && message.toLowerCase(Locale.ROOT).contains("uq_users_normalized_email");
    }
}
