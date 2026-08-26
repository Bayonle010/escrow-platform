package io.github.bayonle010.escrow.escrow.creation.domain;

public enum EscrowState {
    AWAITING_COUNTERPARTY,
    AWAITING_FUNDING,
    FUNDING_PROCESSING,
    FUNDED,
    RELEASED,
    REFUNDED,
    CANCELLED
}
