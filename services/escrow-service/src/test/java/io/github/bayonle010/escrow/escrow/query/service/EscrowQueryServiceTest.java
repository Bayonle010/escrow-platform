package io.github.bayonle010.escrow.escrow.query.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.bayonle010.escrow.escrow.creation.domain.EscrowState;
import io.github.bayonle010.escrow.escrow.creation.entity.EscrowEntity;
import io.github.bayonle010.escrow.escrow.creation.repository.EscrowRepository;
import io.github.bayonle010.escrow.escrow.shared.exception.EscrowNotFoundException;

class EscrowQueryServiceTest {

    private final EscrowRepository repository = mock(EscrowRepository.class);
    private final EscrowQueryService service = new EscrowQueryService(repository);

    @Test
    void returnsTheAuthoritativeFundingSnapshot() {
        UUID escrowId = UUID.fromString("019c0000-0000-7000-8000-000000000020");
        EscrowEntity escrow = EscrowEntity.builder()
                .escrowId(escrowId)
                .buyerId(UUID.fromString("019c0000-0000-7000-8000-000000000001"))
                .sellerId(UUID.fromString("019c0000-0000-7000-8000-000000000002"))
                .amountMinor(100000)
                .currency("NGN")
                .currentTermsVersion(1)
                .state(EscrowState.AWAITING_FUNDING)
                .deliveryDeadline(Instant.parse("2099-09-30T12:00:00Z"))
                .createdAt(Instant.parse("2026-08-20T12:00:00Z"))
                .updatedAt(Instant.parse("2026-08-21T12:00:00Z"))
                .build();
        when(repository.findById(escrowId)).thenReturn(Optional.of(escrow));

        var result = service.get(escrowId);

        assertThat(result.escrowId()).isEqualTo(escrowId);
        assertThat(result.amountMinor()).isEqualTo(100000);
        assertThat(result.currency()).isEqualTo("NGN");
        assertThat(result.state()).isEqualTo(EscrowState.AWAITING_FUNDING);
    }

    @Test
    void rejectsAnUnknownEscrow() {
        UUID escrowId = UUID.fromString("019c0000-0000-7000-8000-000000000020");
        when(repository.findById(escrowId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(escrowId))
                .isInstanceOf(EscrowNotFoundException.class);
    }
}
