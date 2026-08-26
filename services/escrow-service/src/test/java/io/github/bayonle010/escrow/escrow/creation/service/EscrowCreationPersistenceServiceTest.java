package io.github.bayonle010.escrow.escrow.creation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import io.github.bayonle010.escrow.escrow.creation.builder.EscrowCreationEntityBuilder;
import io.github.bayonle010.escrow.escrow.creation.domain.EscrowCreation;
import io.github.bayonle010.escrow.escrow.creation.domain.EscrowState;
import io.github.bayonle010.escrow.escrow.creation.entity.EscrowEntity;
import io.github.bayonle010.escrow.escrow.creation.entity.EscrowTermsEntity;
import io.github.bayonle010.escrow.escrow.creation.entity.OutboxEventEntity;
import io.github.bayonle010.escrow.escrow.creation.repository.EscrowRepository;
import io.github.bayonle010.escrow.escrow.creation.repository.EscrowTermsRepository;
import io.github.bayonle010.escrow.escrow.creation.repository.OutboxEventRepository;

class EscrowCreationPersistenceServiceTest {

    @Test
    void savesEscrowTermsAndOutboxEventBeforeFlushingTheTransaction() {
        EscrowRepository escrowRepository = mock(EscrowRepository.class);
        EscrowTermsRepository termsRepository = mock(EscrowTermsRepository.class);
        OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);
        EscrowCreationEntityBuilder entityBuilder = mock(EscrowCreationEntityBuilder.class);
        EscrowEntity escrowEntity = mock(EscrowEntity.class);
        EscrowTermsEntity termsEntity = mock(EscrowTermsEntity.class);
        OutboxEventEntity outboxEventEntity = mock(OutboxEventEntity.class);
        UUID escrowId = UUID.fromString("019c0000-0000-7000-8000-000000000020");
        EscrowCreation creation = creation();
        when(entityBuilder.buildEscrow(creation)).thenReturn(escrowEntity);
        when(escrowRepository.save(escrowEntity)).thenReturn(escrowEntity);
        when(escrowEntity.getEscrowId()).thenReturn(escrowId);
        when(entityBuilder.buildTerms(creation, escrowId)).thenReturn(termsEntity);
        when(entityBuilder.buildOutboxEvent(creation, escrowId)).thenReturn(outboxEventEntity);
        EscrowCreationPersistenceService service = new EscrowCreationPersistenceService(
                escrowRepository,
                termsRepository,
                outboxEventRepository,
                entityBuilder);

        UUID savedEscrowId = service.save(creation);

        InOrder persistenceOrder = inOrder(escrowRepository, termsRepository, outboxEventRepository);
        persistenceOrder.verify(escrowRepository).save(escrowEntity);
        persistenceOrder.verify(termsRepository).save(termsEntity);
        persistenceOrder.verify(outboxEventRepository).save(outboxEventEntity);
        persistenceOrder.verify(outboxEventRepository).flush();
        assertThat(savedEscrowId).isEqualTo(escrowId);
    }

    private EscrowCreation creation() {
        UUID buyerId = UUID.fromString("019c0000-0000-7000-8000-000000000001");
        return new EscrowCreation(
                buyerId,
                UUID.fromString("019c0000-0000-7000-8000-000000000002"),
                buyerId,
                100000,
                "NGN",
                "Professional camera",
                "GOODS",
                Instant.parse("2026-09-30T12:00:00Z"),
                7,
                "Release",
                "Refund",
                1,
                EscrowState.AWAITING_COUNTERPARTY,
                Instant.parse("2026-08-20T12:00:00Z"),
                UUID.fromString("019c0000-0000-7000-8000-000000000010"));
    }
}
