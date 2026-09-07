package io.github.bayonle010.escrow.escrow.creation.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.github.bayonle010.escrow.escrow.creation.domain.EscrowState;

class EscrowEntityFundingTest {

    private static final Instant PREVIOUS_UPDATE = Instant.parse("2026-09-04T09:00:00Z");
    private static final Instant FUNDED_AT = Instant.parse("2026-09-04T10:00:00Z");

    @Test
    void transitionsAnAwaitingFundingEscrowToFunded() {
        EscrowEntity escrow = escrowIn(EscrowState.AWAITING_FUNDING);

        escrow.secureFunding(FUNDED_AT);

        assertThat(escrow.getState()).isEqualTo(EscrowState.FUNDED);
        assertThat(escrow.getUpdatedAt()).isEqualTo(FUNDED_AT);
    }

    @Test
    void rejectsFundingWhenEscrowIsNotAwaitingFunding() {
        EscrowEntity escrow = escrowIn(EscrowState.FUNDED);

        assertThatThrownBy(() -> escrow.secureFunding(FUNDED_AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FUNDED");

        assertThat(escrow.getState()).isEqualTo(EscrowState.FUNDED);
        assertThat(escrow.getUpdatedAt()).isEqualTo(PREVIOUS_UPDATE);
    }

    private EscrowEntity escrowIn(EscrowState state) {
        return EscrowEntity.builder()
                .state(state)
                .updatedAt(PREVIOUS_UPDATE)
                .build();
    }
}
