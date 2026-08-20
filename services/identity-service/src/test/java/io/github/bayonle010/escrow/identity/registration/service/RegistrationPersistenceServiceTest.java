package io.github.bayonle010.escrow.identity.registration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import io.github.bayonle010.escrow.identity.registration.builder.RegistrationEntityBuilder;
import io.github.bayonle010.escrow.identity.registration.domain.UserRegistration;
import io.github.bayonle010.escrow.identity.registration.domain.UserStatus;
import io.github.bayonle010.escrow.identity.registration.entity.OutboxEventEntity;
import io.github.bayonle010.escrow.identity.registration.entity.UserEntity;
import io.github.bayonle010.escrow.identity.registration.repository.OutboxEventRepository;
import io.github.bayonle010.escrow.identity.registration.repository.UserRepository;

class RegistrationPersistenceServiceTest {

    @Test
    void savesUserAndOutboxEventBeforeFlushingTheTransaction() {
        UserRepository userRepository = mock(UserRepository.class);
        OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);
        RegistrationEntityBuilder entityBuilder = mock(RegistrationEntityBuilder.class);
        UserEntity userEntity = mock(UserEntity.class);
        OutboxEventEntity outboxEventEntity = mock(OutboxEventEntity.class);
        UUID generatedUserId = UUID.fromString("019c0000-0000-7000-8000-000000000001");
        UserRegistration registration = new UserRegistration(
                "alice@example.com",
                "alice@example.com",
                "bcrypt-hash",
                UserStatus.PENDING_VERIFICATION,
                Instant.parse("2026-08-20T12:00:00Z"));
        when(entityBuilder.buildUser(registration)).thenReturn(userEntity);
        when(userRepository.save(userEntity)).thenReturn(userEntity);
        when(userEntity.getUserId()).thenReturn(generatedUserId);
        when(entityBuilder.buildOutboxEvent(registration, generatedUserId)).thenReturn(outboxEventEntity);
        RegistrationPersistenceService service = new RegistrationPersistenceService(
                userRepository,
                outboxEventRepository,
                entityBuilder);

        UUID userId = service.save(registration);

        InOrder persistenceOrder = inOrder(userRepository, outboxEventRepository);
        persistenceOrder.verify(userRepository).save(userEntity);
        persistenceOrder.verify(outboxEventRepository).save(outboxEventEntity);
        persistenceOrder.verify(outboxEventRepository).flush();
        assertThat(userId).isEqualTo(generatedUserId);
    }
}
