CREATE TABLE escrows (
    escrow_id UUID PRIMARY KEY,
    buyer_id UUID NOT NULL,
    seller_id UUID NOT NULL,
    current_terms_version INTEGER NOT NULL,
    state VARCHAR(40) NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    inspection_period_days INTEGER NOT NULL,
    delivery_deadline TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_escrows_participants_differ CHECK (buyer_id <> seller_id),
    CONSTRAINT chk_escrows_terms_version CHECK (current_terms_version > 0),
    CONSTRAINT chk_escrows_amount CHECK (amount_minor > 0),
    CONSTRAINT chk_escrows_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_escrows_inspection_period CHECK (inspection_period_days > 0),
    CONSTRAINT chk_escrows_state CHECK (state IN (
        'AWAITING_COUNTERPARTY',
        'AWAITING_FUNDING',
        'FUNDING_PROCESSING',
        'FUNDED',
        'RELEASED',
        'REFUNDED',
        'CANCELLED'
    ))
);

CREATE INDEX idx_escrows_buyer_created
    ON escrows (buyer_id, created_at DESC);

CREATE INDEX idx_escrows_seller_created
    ON escrows (seller_id, created_at DESC);

CREATE TABLE escrow_terms (
    terms_id UUID PRIMARY KEY,
    escrow_id UUID NOT NULL REFERENCES escrows (escrow_id),
    terms_version INTEGER NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    category VARCHAR(100) NOT NULL,
    delivery_deadline TIMESTAMP WITH TIME ZONE NOT NULL,
    inspection_period_days INTEGER NOT NULL,
    release_conditions VARCHAR(2000) NOT NULL,
    refund_conditions VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by UUID NOT NULL,
    CONSTRAINT uq_escrow_terms_version UNIQUE (escrow_id, terms_version),
    CONSTRAINT chk_escrow_terms_version CHECK (terms_version > 0),
    CONSTRAINT chk_escrow_terms_amount CHECK (amount_minor > 0),
    CONSTRAINT chk_escrow_terms_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_escrow_terms_inspection_period CHECK (inspection_period_days > 0)
);

CREATE TABLE outbox_events (
    event_id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_version INTEGER NOT NULL,
    correlation_id UUID NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
    CONSTRAINT chk_outbox_attempts CHECK (attempts >= 0)
);

CREATE INDEX idx_outbox_events_pending
    ON outbox_events (next_attempt_at, occurred_at)
    WHERE status = 'PENDING';
