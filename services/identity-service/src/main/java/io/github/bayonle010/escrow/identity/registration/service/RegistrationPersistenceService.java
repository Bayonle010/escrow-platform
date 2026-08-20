package io.github.bayonle010.escrow.identity.registration.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.bayonle010.escrow.identity.registration.builder.RegistrationEntityBuilder;
import io.github.bayonle010.escrow.identity.registration.domain.UserRegistration;
import io.github.bayonle010.escrow.identity.registration.entity.UserEntity;
import io.github.bayonle010.escrow.identity.registration.repository.OutboxEventRepository;
import io.github.bayonle010.escrow.identity.registration.repository.UserRepository;

@Service
public class RegistrationPersistenceService {

    private final UserRepository userRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final RegistrationEntityBuilder entityBuilder;

    public RegistrationPersistenceService(
            UserRepository userRepository,
            OutboxEventRepository outboxEventRepository,
            RegistrationEntityBuilder entityBuilder) {
        this.userRepository = userRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.entityBuilder = entityBuilder;
    }

    @Transactional
    public UUID save(UserRegistration registration) {
        UserEntity user = userRepository.save(entityBuilder.buildUser(registration));
        outboxEventRepository.save(entityBuilder.buildOutboxEvent(registration, user.getUserId()));
        outboxEventRepository.flush();
        return user.getUserId();
    }
}
