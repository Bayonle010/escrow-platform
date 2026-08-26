package io.github.bayonle010.escrow.escrow.creation.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.bayonle010.escrow.escrow.creation.builder.EscrowCreationEntityBuilder;
import io.github.bayonle010.escrow.escrow.creation.domain.EscrowCreation;
import io.github.bayonle010.escrow.escrow.creation.entity.EscrowEntity;
import io.github.bayonle010.escrow.escrow.creation.repository.EscrowRepository;
import io.github.bayonle010.escrow.escrow.creation.repository.EscrowTermsRepository;
import io.github.bayonle010.escrow.escrow.creation.repository.OutboxEventRepository;

@Service
public class EscrowCreationPersistenceService {

    private final EscrowRepository escrowRepository;
    private final EscrowTermsRepository termsRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final EscrowCreationEntityBuilder entityBuilder;

    public EscrowCreationPersistenceService(
            EscrowRepository escrowRepository,
            EscrowTermsRepository termsRepository,
            OutboxEventRepository outboxEventRepository,
            EscrowCreationEntityBuilder entityBuilder) {
        this.escrowRepository = escrowRepository;
        this.termsRepository = termsRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.entityBuilder = entityBuilder;
    }

    @Transactional
    public UUID save(EscrowCreation creation) {
        EscrowEntity escrow = escrowRepository.save(entityBuilder.buildEscrow(creation));
        UUID escrowId = escrow.getEscrowId();
        termsRepository.save(entityBuilder.buildTerms(creation, escrowId));
        outboxEventRepository.save(entityBuilder.buildOutboxEvent(creation, escrowId));
        outboxEventRepository.flush();
        return escrowId;
    }
}
