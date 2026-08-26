CREATE TABLE payments (
    payment_id UUID PRIMARY KEY,
    escrow_id UUID NOT NULL,
    payer_id UUID NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    provider VARCHAR(40) NOT NULL,
    provider_reference VARCHAR(200),
    status VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(36) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_payments_escrow UNIQUE (escrow_id),
    CONSTRAINT uq_payments_payer_idempotency UNIQUE (payer_id, idempotency_key),
    CONSTRAINT chk_payments_amount CHECK (amount_minor > 0),
    CONSTRAINT chk_payments_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_payments_idempotency_key CHECK (
        idempotency_key ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[47][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
    ),
    CONSTRAINT chk_payments_status CHECK (status IN (
        'PROCESSING', 'SUCCEEDED', 'FAILED', 'UNKNOWN', 'CANCELLED'
    ))
);

CREATE INDEX idx_payments_payer_created
    ON payments (payer_id, created_at DESC);

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
